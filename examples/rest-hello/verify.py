"""Actual HTTP contract checks; produces a JUnit report without extra packages."""
import json
import sys
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
from pathlib import Path

url, language = sys.argv[1:3]
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def request(path, method='GET'):
    req = urllib.request.Request(url + path, method=method)
    try:
        response = opener.open(req, timeout=3)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers.get('Content-Type', ''), json.load(response)

for attempt in range(30):
    try:
        request('/hello')
        break
    except (OSError, ValueError):
        time.sleep(1)

suite = ET.Element('testsuite', name=language, tests='3', failures='0')
failed = 0
for name, path, method, expected in [('hello', '/hello', 'GET', 200), ('not-found', '/missing', 'GET', 404), ('method', '/hello', 'POST', 405)]:
    case = ET.SubElement(suite, 'testcase', name=name, classname=language)
    try:
        status, content_type, body = request(path, method)
        assert status == expected, (status, expected)
        assert 'application/json' in content_type, content_type
        if expected == 200:
            assert body == {'message': 'hello', 'language': language}, body
        else:
            assert 'error' in body, body
        print('PASS', language, method, path, status, body, flush=True)
    except Exception as error:
        failed += 1
        ET.SubElement(case, 'failure', message=str(error)).text = repr(error)
        print('FAIL', language, name, repr(error), flush=True)
suite.set('failures', str(failed))
Path('reports').mkdir(exist_ok=True)
ET.ElementTree(suite).write('reports/' + language + '.xml', encoding='utf-8', xml_declaration=True)
sys.exit(1 if failed else 0)
