"""Backup offline do banco e originais necessários; sem dependências Python externas."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from urllib.parse import urlparse
from uuid import UUID


def pg_tool(name):
    found = shutil.which(name)
    if found:
        return found
    configured = os.environ.get('PG_BIN')
    candidates = [Path(configured)] if configured else []
    candidates += sorted(Path(os.environ.get('ProgramFiles', 'C:/Program Files')).glob('PostgreSQL/*/bin'), reverse=True)
    for folder in candidates:
        executable = folder / (name + ('.exe' if os.name == 'nt' else ''))
        if executable.is_file():
            return str(executable)
    raise ValueError(f'{name} não encontrado. Configure PG_BIN com a pasta dos utilitários PostgreSQL.')


def run_pg(args, name, extra, capture=False):
    env = os.environ.copy()
    if not env.get('PGPASSWORD') and env.get('DB_PASSWORD'):
        env['PGPASSWORD'] = env['DB_PASSWORD']
    env['PGCLIENTENCODING'] = 'UTF8'
    temporary_sql = None
    try:
        # psql no Windows pode codificar argumentos -c na página de código local.
        # Arquivo UTF-8 mantém os caminhos e as mensagens corretos em qualquer SO.
        extra = list(extra)
        if name == 'psql' and '-c' in extra:
            position = extra.index('-c')
            with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', prefix='transcreve-sql-', suffix='.sql', delete=False) as file:
                file.write(extra[position + 1]); temporary_sql = Path(file.name)
            extra[position:position + 2] = ['-f', str(temporary_sql)]
        command = [pg_tool(name), '-h', args.host, '-p', str(args.port), '-U', args.user, '-w', *extra]
        result = subprocess.run(command, env=env, capture_output=True, encoding='utf-8')
    finally:
        if temporary_sql is not None:
            temporary_sql.unlink(missing_ok=True)
    if result.returncode:
        # stderr pode conter SQL, texto ou caminhos; não publicar seu conteúdo.
        raise ValueError(f'{name} falhou (saída {result.returncode}). Confira conexão, permissões e versão do PostgreSQL.')
    return result.stdout if capture else None


def backend_stopped(url):
    parsed = urlparse(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname:
        raise ValueError('URL do backend inválida.')
    try:
        connection = socket.create_connection((parsed.hostname, parsed.port or (443 if parsed.scheme == 'https' else 80)), timeout=5)
    except ConnectionRefusedError:
        return
    except OSError as error:
        raise ValueError('Não foi possível confirmar que o backend está parado.') from error
    connection.close()
    raise ValueError('Pare o backend antes desta operação. Não pode haver outros escritores no banco ou nos uploads.')


def within(path, root):
    resolved, base = Path(path).resolve(), Path(root).resolve()
    if not resolved.is_relative_to(base) or resolved == base:
        raise ValueError('Arquivo fora da pasta de uploads autorizada.')
    return resolved


def digest(path):
    hasher = hashlib.sha256()
    with Path(path).open('rb') as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b''):
            hasher.update(chunk)
    return hasher.hexdigest()


def jobs(args, condition='TRUE'):
    sql = "SELECT coalesce(json_agg(row_to_json(t)), '[]') FROM (SELECT id, caminho_arquivo, status FROM transcricao WHERE " + condition + ') t;'
    return json.loads(run_pg(args, 'psql', ['-d', args.database, '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', sql], True))


def stored_name(job):
    job_id = str(UUID(job['id']))
    suffix = Path(job['caminho_arquivo']).suffix.lower()
    if not re.fullmatch(r'\.[a-z0-9]{1,10}', suffix):
        suffix = '.audio'
    return job_id + suffix


def verify(folder):
    root = Path(folder).resolve()
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('version') != 1 or not manifest.get('complete'):
        raise ValueError('Backup incompleto ou versão não suportada.')
    for name, expected in manifest['sha256'].items():
        file = within(root / name, root)
        if not file.is_file() or digest(file) != expected:
            raise ValueError('Backup corrompido: arquivo ausente ou hash divergente.')
    if 'database.dump' not in manifest['sha256']:
        raise ValueError('Backup sem banco de dados.')
    for job in manifest['jobs']:
        UUID(job['id'])
        if not re.fullmatch(r'[a-f0-9-]{36}\.[a-z0-9]{1,10}', job['file']):
            raise ValueError('Nome de original inválido no backup.')
        if job['originalPresent'] and 'audio/' + job['file'] not in manifest['sha256']:
            raise ValueError('Original não foi incluído na verificação.')
    return manifest


def backup(args):
    backend_stopped(args.backend_url)
    source = Path(args.uploads).resolve()
    destination = Path(args.output).resolve()
    if destination.exists() or destination == source or destination.is_relative_to(source):
        raise ValueError('Use uma pasta nova de backup, fora dos uploads.')
    destination.mkdir(parents=True)
    (destination / 'audio').mkdir()
    manifest = {'version': 1, 'complete': False, 'createdAt': datetime.now(timezone.utc).isoformat(),
                'sourceDatabase': args.database, 'sourceHost': args.host, 'sourcePort': args.port,
                'jobs': [], 'sha256': {}}
    run_pg(args, 'pg_dump', ['-d', args.database, '-Fc', '--no-owner', '--no-privileges', '-f', str(destination / 'database.dump')])
    for job in jobs(args):
        name = stored_name(job)
        present = False
        if job['status'] in ('ERRO', 'PENDENTE', 'PROCESSANDO'):
            original = within(job['caminho_arquivo'], source)
            present = original.is_file()
            if not present and job['status'] != 'ERRO':
                raise ValueError('Um job em andamento não possui original; backup não concluído.')
            if present:
                shutil.copyfile(original, destination / 'audio' / name)
                manifest['sha256']['audio/' + name] = digest(destination / 'audio' / name)
        manifest['jobs'].append({'id': str(UUID(job['id'])), 'file': name, 'status': job['status'], 'originalPresent': present})
    manifest['sha256']['database.dump'] = digest(destination / 'database.dump')
    manifest['complete'] = True
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    verify(destination)
    print(f'Backup verificado: {len(manifest["jobs"])} jobs; {len(manifest["sha256"]) - 1} originais.')


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def restore(args):
    backend_stopped(args.backend_url)
    if not re.fullmatch(r'transcreve_restore_[a-z0-9_]+', args.database):
        raise ValueError('Restaure em um banco NOVO com prefixo transcreve_restore_. O banco de uso não será sobrescrito.')
    manifest = verify(args.backup)
    destination = Path(args.uploads).resolve()
    if destination.exists():
        raise ValueError('A pasta de uploads da restauração deve ser nova.')
    backup_root = Path(args.backup).resolve()
    if destination.is_relative_to(backup_root):
        raise ValueError('Restaure fora da pasta de backup.')
    # createdb falha se o banco existe. Nunca DROP, --clean ou sobrescrever.
    run_pg(args, 'createdb', [args.database])
    run_pg(args, 'pg_restore', ['-d', args.database, '--exit-on-error', '--no-owner', '--no-privileges', str(backup_root / 'database.dump')])
    destination.mkdir(parents=True)
    updates = []
    for job in manifest['jobs']:
        path = within(destination / job['file'], destination)
        if job['originalPresent']:
            shutil.copyfile(backup_root / 'audio' / job['file'], path)
            if digest(path) != manifest['sha256']['audio/' + job['file']]:
                raise ValueError('Original restaurado com hash divergente.')
        updates.append('UPDATE transcricao SET caminho_arquivo=' + literal(path) + ' WHERE id=' + literal(job['id']) + ';')
    sql = 'BEGIN;\n' + '\n'.join(updates) + '\nCOMMIT;'
    run_pg(args, 'psql', ['-d', args.database, '-X', '-v', 'ON_ERROR_STOP=1', '-c', sql])
    print('Restauração concluída em banco e uploads isolados. Não inicie o processador antes de conferir os dados.')


def prune(args):
    backend_stopped(args.backend_url)
    if args.days < 1:
        raise ValueError('Retenção deve ser de pelo menos um dia.')
    condition = "status='ERRO' AND atualizado_em < CURRENT_TIMESTAMP - INTERVAL '" + str(args.days) + " days'"
    selected = jobs(args, condition)
    originals = []
    for job in selected:
        path = within(job['caminho_arquivo'], args.uploads)
        if path.is_file():
            originals.append((job, path))
    print(f'Originais elegíveis para retenção de {args.days} dias: {len(originals)}.')
    if not args.apply:
        print('Simulação: nenhum arquivo foi removido. Use --apply e --backup após verificar o backup.')
        return
    if not args.backup:
        raise ValueError('Limpeza exige --backup de um backup verificado dos originais elegíveis.')
    manifest = verify(args.backup)
    if (manifest['sourceDatabase'], manifest['sourceHost'], manifest['sourcePort']) != (args.database, args.host, args.port):
        raise ValueError('Backup pertence a outro banco.')
    backed = {job['id']: job for job in manifest['jobs']}
    for job, path in originals:
        saved = backed.get(job['id'])
        if not saved or not saved['originalPresent'] or digest(path) != manifest['sha256'].get('audio/' + saved['file']):
            raise ValueError('Original elegível sem cópia correspondente no backup. Faça um backup novo.')
    # Todos os caminhos foram resolvidos dentro da raiz e todos os hashes conferidos.
    for job, path in originals:
        within(path, args.uploads).unlink()
        run_pg(args, 'psql', ['-d', args.database, '-X', '-v', 'ON_ERROR_STOP=1', '-c',
               "UPDATE transcricao SET codigo_erro='ORIGINAL_AUSENTE', mensagem_erro='Áudio removido após o período de retenção. Envie o áudio novamente.' WHERE id=" + literal(job['id']) + ';'])
    print(f'Limpeza aplicada: {len(originals)} originais removidos; jobs, textos e checkpoints preservados.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['backup', 'verify', 'restore', 'prune'])
    parser.add_argument('--host', default='localhost'); parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--user', default='postgres'); parser.add_argument('--database', default='transcricao')
    parser.add_argument('--backend-url', default='http://127.0.0.1:8080')
    parser.add_argument('--uploads'); parser.add_argument('--output'); parser.add_argument('--backup')
    parser.add_argument('--days', type=int, default=30); parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    required = {'backup': ['uploads', 'output'], 'verify': ['backup'], 'restore': ['uploads', 'backup'], 'prune': ['uploads']}[args.operation]
    if any(not getattr(args, name) for name in required):
        parser.error('Informe ' + ', '.join('--' + name for name in required))
    if args.operation == 'verify':
        verify(args.backup); print('Backup íntegro; dump e originais conferidos por SHA-256.')
    else:
        globals()[args.operation](args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        print('Operação não concluída: ' + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
