"""Create a NEW private CA/server bundle for one RFC1918 IPv4 MQTT listener.

Requires cryptography on the development PC. No private key material is printed.
Keep authority/ca.key on the development PC; only certs/ and mqtt-lan.env go to server.
"""
import argparse
from datetime import datetime, timedelta, timezone
import ipaddress
import os
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


def lan_address(value):
    address = ipaddress.IPv4Address(value)
    networks = ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
    if not any(address in ipaddress.IPv4Network(net) for net in networks):
        raise ValueError("Use a private LAN IPv4")
    return address


def write_new(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as stream:
        stream.write(data)


def create(directory, ip):
    address = lan_address(ip)
    directory = Path(directory).resolve()
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if any(directory.iterdir()):
        raise FileExistsError("Use a new empty TLS directory")
    now = datetime.now(timezone.utc)
    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Ppossak private MQTT CA")])
    ca = (x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name)
        .public_key(ca_key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
            data_encipherment=False, key_agreement=False, key_cert_sign=True, crl_sign=True,
            encipher_only=False, decipher_only=False), critical=True)
        .sign(ca_key, hashes.SHA256()))
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    cert = (x509.CertificateBuilder()
        .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Ppossak LAN MQTT")]))
        .issuer_name(ca_name).public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=365))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(address)]), critical=False)
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
        .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=True,
            data_encipherment=False, key_agreement=False, key_cert_sign=False, crl_sign=False,
            encipher_only=False, decipher_only=False), critical=True)
        .sign(ca_key, hashes.SHA256()))
    (directory / "authority").mkdir(mode=0o700)
    (directory / "certs").mkdir(mode=0o700)
    pem = serialization.Encoding.PEM
    key_format = serialization.PrivateFormat.PKCS8
    content = {
        "authority/ca.key": ca_key.private_bytes(pem, key_format, serialization.NoEncryption()),
        "certs/ca.crt": ca.public_bytes(pem),
        "certs/server.crt": cert.public_bytes(pem),
        "certs/server.key": key.private_bytes(pem, key_format, serialization.NoEncryption()),
        "mqtt-lan.env": ("PPOSSAK_MQTT_LAN_IP=" + str(address) + "\n").encode(),
    }
    created = []
    try:
        for name, data in content.items():
            path = directory / name
            write_new(path, data)
            created.append(path)
        return created
    except BaseException:
        for path in created:
            path.unlink()
        (directory / "authority").rmdir()
        (directory / "certs").rmdir()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lan-ip", required=True)
    parser.add_argument("--directory", type=Path, default=Path(".tools/deployment/mqtt-lan"))
    args = parser.parse_args()
    try:
        create(args.directory, args.lan_ip)
    except (OSError, ValueError):
        parser.exit(1, "TLS setup failed. Use a valid LAN IPv4 and a new private folder. Values withheld.\n")
    print("Created private MQTT TLS bundle; key contents hidden. Keep authority/ on this PC.")


if __name__ == "__main__":
    main()
