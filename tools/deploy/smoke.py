"""Confere HTTPS, sessão, CSRF e histórico sem chamar o provedor."""
import argparse
import http.cookiejar
import json
import os
import ssl
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('url')
    parser.add_argument('--ca-file')
    args = parser.parse_args()
    base = args.url.rstrip('/')
    if urllib.parse.urlparse(base).scheme != 'https':
        raise ValueError('A validação de publicação exige HTTPS.')
    email, password = os.environ.get('ADMIN_EMAIL'), os.environ.get('ADMIN_PASSWORD')
    if not email or not password:
        raise ValueError('Defina ADMIN_EMAIL e ADMIN_PASSWORD no ambiente.')
    cookies = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cookies),
        urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=args.ca_file)))

    def request(path, data=None, headers=None):
        req = urllib.request.Request(base + path, data=data, headers=headers or {})
        try:
            response = client.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        return response.status, response.read(), response.headers

    status, body, _ = request('/')
    assert status == 200 and b'Transcreve' in body, 'Interface indisponível'
    assert request('/transcricoes')[0] == 401, 'Histórico exposto sem sessão'
    assert request('/auth/login', b'')[0] == 403, 'Login sem CSRF foi aceito'
    status, body, _ = request('/auth/csrf')
    assert status == 200, 'CSRF indisponível'
    csrf = json.loads(body)
    headers = {csrf['headerName']: csrf['token'], 'Content-Type': 'application/x-www-form-urlencoded'}
    status, _, _ = request('/auth/login', urllib.parse.urlencode({'email': email, 'password': password}).encode(), headers)
    assert status == 204, 'Login falhou'
    session = next((c for c in cookies if c.name == 'JSESSIONID'), None)
    assert session and session.secure and session.has_nonstandard_attr('HttpOnly'), 'Cookie sem proteção'
    assert session.get_nonstandard_attr('SameSite', '').lower() == 'lax', 'Cookie sem SameSite'
    assert request('/auth/me')[0] == 200, 'Sessão não foi preservada no proxy'
    status, body, _ = request('/transcricoes?pagina=0')
    assert status == 200, 'Histórico indisponível'
    items = json.loads(body)['itens']
    assert all('texto' not in item and 'caminhoArquivo' not in item for item in items), 'Histórico contém dados completos'
    if items:
        status, body, _ = request('/transcricoes/' + items[0]['id'])
        assert status == 200 and 'texto' in json.loads(body), 'Detalhe não preserva contrato'
    csrf = json.loads(request('/auth/csrf')[1])
    assert request('/auth/logout', b'', {csrf['headerName']: csrf['token']})[0] == 204, 'Logout falhou'
    assert request('/transcricoes')[0] == 401, 'Sessão permaneceu após logout'
    print('OK: HTTPS, interface, sessão segura, CSRF, histórico leve e logout.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Nunca imprime requisições, credenciais ou respostas contendo textos privados.
        print('Falha na validação:', str(error) if isinstance(error, (ValueError, AssertionError)) else type(error).__name__)
        raise SystemExit(1)
