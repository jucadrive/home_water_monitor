"""Send host-C-generated temperature fixtures through an explicitly isolated acceptance server.

Uses public test credentials only; never reads deployment secrets or calls the mini PC.
Start androidAcceptanceServer with acceptanceTemperaturePayload first. No real device.
"""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import time
import urllib.request
import urllib.error
from urllib.parse import urlsplit
import uuid
from personal_mqtt_probe import Wire

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ready", required=True, type=Path)
    parser.add_argument("--payloads", type=Path, default=Path(".tools/temperature/host"))
    parser.add_argument("--output", type=Path, default=Path(".tools/temperature/wire"))
    args = parser.parse_args()
    ready = json.loads(args.ready.read_text())
    origin, broker = urlsplit(ready['origin']), urlsplit(ready['brokerUri'])
    assert origin.scheme == 'http' and origin.hostname == '127.0.0.1' and origin.port
    assert broker.scheme == 'tcp' and broker.hostname == '127.0.0.1' and broker.port
    assert ready['source'] == 'SIMULATED' and ready['temperatureFixture'] is True
    args.output.mkdir(parents=True, exist_ok=True)
    token = None
    def request(path, body=None):
        data = None if body is None else json.dumps(body).encode()
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(ready['origin'] + path, data=data, headers=headers)
        with urllib.request.urlopen(req, timeout=10) as response: return json.load(response)
    token = request('/api/auth/login', {'username':'fixture-owner','password':'fixture-password-only'})['accessToken']
    wire = Wire(args.output, ready['brokerUri'], '', '')
    device = ready['deviceId']; prefix = 'home/water/'
    wire.listen([prefix+'v1/'+device+'/preservation', prefix+'v2/'+device+'/preservation'])
    cases = []
    def ack(session, version=2):
        deadline = time.monotonic()+12
        while time.monotonic()<deadline:
            event = wire.receive(.2)
            if event and event['body'].get('sessionId') == session:
                assert event['topic'] == prefix+f'v{version}/'+device+'/preservation'
                assert event['qos'] == 1 and not event['retained'] and event['pfi'] and event['contentType'] == 'application/json'
                assert event['body']['schemaVersion'] == version
                return event['body']
        raise AssertionError('No matching preservation confirmation')
    try:
        for path in sorted(args.payloads.glob('c-temperature-*-prefix-*.json')):
            payload = json.loads(path.read_text()); payload['sessionId'] = str(uuid.uuid4()); payload['deviceId'] = device
            count = len(payload['measurements']); session = payload['sessionId']
            assert wire.publish(prefix+'v2/'+device+'/events', payload)
            confirmation = ack(session)
            assert confirmation['preservedSampleCount'] == count
            assert confirmation['preservationResult'] == ('NORMAL' if count==60 else 'INCOMPLETE')
            detail = request('/api/measurement-sessions/'+session)
            assert detail['session']['temperatureSource'] == 'SIMULATED' and detail['session']['temperatureAssumedC'] is None
            assert detail['session']['statsSampleCount'] == (50 if count==60 else 0)
            assert len(detail['measurements']) == count
            for stored, incoming in zip(detail['measurements'], payload['measurements']):
                for key in ['temperatureC','tdsPpm','rawVoltageMv']:
                    assert Decimal(str(stored[key])) == Decimal(str(incoming[key]))
                assert stored['temperatureElapsedMs'] == incoming['temperatureElapsedMs']
            assert wire.publish(prefix+'v2/'+device+'/events', payload)
            replay = ack(session)['comparison']
            assert replay['identicalCount'] == count and replay['conflictCount'] == 0
            if count==60:
                original = payload['measurements'][0]['temperatureC']
                payload['measurements'][0]['temperatureC'] = original+1
                assert wire.publish(prefix+'v2/'+device+'/events', payload)
                assert ack(session)['comparison']['conflictCount'] == 1
                assert Decimal(str(request('/api/measurement-sessions/'+session)['measurements'][0]['temperatureC'])) == Decimal(str(original))
            cases.append({'fixture':path.name,'count':count,'status':'PASS'})
        assert len(cases) == 12
        invalid = json.loads((args.payloads/'c-temperature-250000-prefix-60.json').read_text())
        invalid['sessionId'] = str(uuid.uuid4())
        invalid['measurements'][0]['temperatureElapsedMs'] = invalid['measurements'][0]['elapsedMs']+1
        assert wire.publish(prefix+'v2/'+device+'/events', invalid)
        deadline = time.monotonic()+2
        while time.monotonic()<deadline:
            event=wire.receive(.1)
            assert not event or event['body'].get('sessionId') != invalid['sessionId']
        try: request('/api/measurement-sessions/'+invalid['sessionId'])
        except urllib.error.HTTPError as error: assert error.code == 404
        else: raise AssertionError('Invalid observation was stored')
    finally:
        wire.close()
        if token:
            req=urllib.request.Request(ready['origin']+'/api/auth/logout', data=b'', headers={'Authorization':'Bearer '+token}, method='POST')
            with urllib.request.urlopen(req, timeout=10): pass
    result={'status':'PASS','normal_temperatures_c':[20,25,30],'cases':cases,'invalid_future_observation':'REJECTED'}
    (args.output/'result.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result))

if __name__ == '__main__': main()
