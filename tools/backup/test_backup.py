import argparse
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4
import transcreve_backup as tool


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='transcreve-backup-test-')
        self.root = Path(self.tmp.name)
        assert self.root.resolve().parent == Path(tempfile.gettempdir()).resolve()
        assert self.root.name.startswith('transcreve-backup-test-')
        self.uploads = self.root / 'uploads'; self.uploads.mkdir()
        self.original = self.uploads / 'aula.mp3'; self.original.write_bytes(b'audio teste')
        self.job = {'id': str(uuid4()), 'caminho_arquivo': str(self.original), 'status': 'ERRO'}
        self.args = argparse.Namespace(host='localhost', port=5432, user='postgres', database='transcricao',
                                       backend_url='http://localhost:8080', uploads=str(self.uploads),
                                       output=str(self.root / 'backup'), backup=None, days=30, apply=False)

    def tearDown(self):
        self.tmp.cleanup()

    def make_backup(self):
        def fake_pg(args, name, extra, capture=False):
            if name == 'pg_dump':
                Path(extra[-1]).write_bytes(b'dump sintetico')
        with patch.object(tool, 'backend_stopped'), patch.object(tool, 'jobs', return_value=[self.job]), patch.object(tool, 'run_pg', side_effect=fake_pg):
            tool.backup(self.args)
        self.args.backup = self.args.output

    def test_backup_verifica_hashes_e_detecta_corrupcao(self):
        self.make_backup(); manifest = tool.verify(self.args.backup)
        self.assertTrue(manifest['jobs'][0]['originalPresent'])
        (Path(self.args.backup) / 'database.dump').write_bytes(b'alterado')
        with self.assertRaises(ValueError): tool.verify(self.args.backup)

    def test_nao_copia_arquivo_fora_da_raiz(self):
        self.job['caminho_arquivo'] = str(self.root / 'outro.mp3')
        with patch.object(tool, 'backend_stopped'), patch.object(tool, 'jobs', return_value=[self.job]), patch.object(tool, 'run_pg'):
            with self.assertRaises(ValueError): tool.backup(self.args)

    def test_restore_recusa_banco_de_uso_antes_de_executar_postgres(self):
        self.make_backup()
        with patch.object(tool, 'backend_stopped'), patch.object(tool, 'run_pg') as pg:
            with self.assertRaises(ValueError): tool.restore(self.args)
            pg.assert_not_called()

    def test_backend_ativo_impede_operacao(self):
        with patch.object(tool.socket, 'create_connection'):
            with self.assertRaises(ValueError): tool.backend_stopped('http://localhost:8080')

    def test_retencao_simulada_e_aplicada_somente_com_backup_correspondente(self):
        self.make_backup()
        with patch.object(tool, 'backend_stopped'), patch.object(tool, 'jobs', return_value=[self.job]), patch.object(tool, 'run_pg'):
            tool.prune(self.args); self.assertTrue(self.original.exists())
            self.args.apply = True
            self.original.write_bytes(b'audio novo diferente')
            with self.assertRaises(ValueError): tool.prune(self.args)
            self.assertTrue(self.original.exists())
            self.original.write_bytes(b'audio teste')
            tool.prune(self.args); self.assertFalse(self.original.exists())

    def test_manifesto_nao_aceita_traversal(self):
        self.make_backup()
        file = Path(self.args.backup) / 'manifest.json'
        manifest = json.loads(file.read_text()); manifest['sha256']['../outside'] = 'x'
        file.write_text(json.dumps(manifest))
        with self.assertRaises(ValueError): tool.verify(self.args.backup)

    def test_restore_reescreve_caminhos_e_preserva_originais(self):
        self.make_backup(); self.args.database = 'transcreve_restore_unit'
        self.args.uploads = str(self.root / 'restored')
        with patch.object(tool, 'backend_stopped'), patch.object(tool, 'run_pg') as pg:
            tool.restore(self.args)
            calls = pg.call_args_list
            self.assertEqual([call.args[1] for call in calls], ['createdb', 'pg_restore', 'psql'])
            self.assertIn('UPDATE transcricao SET caminho_arquivo=', calls[-1].args[2][-1])
            file = Path(self.args.uploads) / tool.stored_name(self.job)
            self.assertEqual(file.read_bytes(), b'audio teste')


@unittest.skipUnless(os.environ.get('RUN_PG_BACKUP_TESTS') == '1', 'Teste PostgreSQL opcional e isolado')
class PostgresRoundTripTest(unittest.TestCase):
    def test_dump_restore_preserva_jobs_cota_textos_checkpoints_e_audio(self):
        suffix = uuid4().hex[:12]
        source_db, target_db = 'transcreve_restore_source_' + suffix, 'transcreve_restore_target_' + suffix
        with tempfile.TemporaryDirectory(prefix='transcreve-pg-backup-test-') as folder:
            root = Path(folder); uploads = root / 'uploads'; uploads.mkdir()
            assert root.resolve().parent == Path(tempfile.gettempdir()).resolve()
            assert root.name.startswith('transcreve-pg-backup-test-')
            audio = uploads / 'aula.mp3'; audio.write_bytes(b'original de teste')
            args = argparse.Namespace(host=os.environ.get('PGHOST', 'localhost'), port=int(os.environ.get('PGPORT', '5432')),
                user=os.environ.get('PGUSER', 'postgres'), database=source_db, backend_url='http://127.0.0.1:8080',
                uploads=str(uploads), output=str(root / 'backup'), backup=str(root / 'backup'), days=30, apply=False)
            created = []
            try:
                tool.run_pg(args, 'createdb', [source_db]); created.append(source_db)
                migrations = Path(__file__).resolve().parents[2] / 'backend/src/main/resources/db/migration'
                for sql_file in sorted(migrations.glob('V*.sql')):
                    tool.run_pg(args, 'psql', ['-d', source_db, '-X', '-v', 'ON_ERROR_STOP=1', '-f', str(sql_file)])
                user_id, job_id = str(uuid4()), str(uuid4())
                sql = f"""INSERT INTO usuario(id,email,senha_hash,role,dia_uso,arquivos_usados)
                    VALUES('{user_id}','fixture@example.com','hash-de-teste','USER',CURRENT_DATE,4);
                    INSERT INTO transcricao(id,usuario_id,nome_arquivo_original,caminho_arquivo,status,total_partes,
                        partes_concluidas,duracao_parte_segundos,criado_em,atualizado_em,codigo_erro,mensagem_erro)
                    VALUES('{job_id}','{user_id}','aula.mp3',{tool.literal(audio)},'ERRO',2,1,900,
                        CURRENT_TIMESTAMP,CURRENT_TIMESTAMP - INTERVAL '40 days','LIMITE_PROVEDOR','mensagem pública');
                    INSERT INTO transcricao_parte(transcricao_id,numero,texto) VALUES('{job_id}',0,'Texto com acentuação.');"""
                tool.run_pg(args, 'psql', ['-d', source_db, '-X', '-v', 'ON_ERROR_STOP=1', '-c', sql])
                tool.backup(args)
                args.database = target_db; args.uploads = str(root / 'restored')
                tool.restore(args); created.append(target_db)
                query = "SELECT t.status || '|' || t.total_partes || '|' || t.partes_concluidas || '|' || t.duracao_parte_segundos || '|' || p.texto || '|' || u.arquivos_usados FROM transcricao t JOIN transcricao_parte p ON p.transcricao_id=t.id JOIN usuario u ON u.id=t.usuario_id;"
                result = tool.run_pg(args, 'psql', ['-d', target_db, '-X', '-A', '-t', '-c', query], True).strip()
                self.assertEqual(result, 'ERRO|2|1|900|Texto com acentuação.|4')
                restored_job = tool.jobs(args)[0]
                self.assertEqual(Path(restored_job['caminho_arquivo']).read_bytes(), b'original de teste')
                # Retenção real somente no banco/pasta descartáveis do teste, após backup próprio.
                args.output = str(root / 'restored-backup'); args.backup = args.output
                tool.backup(args); args.apply = True; tool.prune(args)
                self.assertFalse(Path(restored_job['caminho_arquivo']).exists())
                count = tool.run_pg(args, 'psql', ['-d', target_db, '-X', '-A', '-t', '-c', 'SELECT count(*) FROM transcricao_parte;'], True).strip()
                self.assertEqual(count, '1')
            finally:
                for database in reversed(created):
                    if not database.startswith('transcreve_restore_'): raise AssertionError('Banco de teste inválido')
                    tool.run_pg(args, 'dropdb', [database])


if __name__ == '__main__':
    unittest.main()
