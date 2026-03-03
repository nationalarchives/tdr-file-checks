import defusedxml.ElementTree as ET
import requests
from datetime import datetime


def get_data():
    # Build the SOAP request as a literal string to avoid using Element/SubElement APIs
    # (some linters may not fully recognise those symbols on defusedxml.ElementTree).
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xmlns:xsd="http://www.w3.org/2001/XMLSchema">'
        '<soap:Body>'
        '<getSignatureFileVersionV1 xmlns="http://pronom.nationalarchives.gov.uk" />'
        '</soap:Body>'
        '</soap:Envelope>'
    )
    return xml


def get_latest_droid_version():
    headers = {'Content-Type': 'text/xml;charset=UTF-8',
               'SOAPAction': 'http://pronom.nationalarchives.gov.uk:getSignatureFileVersionV1In'}
    url = 'https://www.nationalarchives.gov.uk/pronom/service.asmx'
    response = requests.post(url, headers=headers, data=get_data(), timeout=10)
    # Parse XML from the response content using defusedxml for safety
    root = ET.fromstring(response.content)
    namespaces = {'soap': 'http://schemas.xmlsoap.org/soap/envelope/'}

    # Find the soap body element and then return the first non-empty text descendant.
    body = root.find('.//soap:Body', namespaces)
    if body is None:
        raise ValueError('No SOAP Body found in response')

    for elem in body.iter():
        if elem is body:
            continue
        if elem.text and elem.text.strip():
            return elem.text.strip()

    raise ValueError('No version found in SOAP response')


def replace_line(line, property_type, new_version, suffix=""):
    if line.startswith(f"{property_type}.version"):
        each_part = line.split("=", 1)
        # handle cases where value may or may not be quoted
        conf_version = each_part[1].strip().strip('"').strip()
        if conf_version != new_version:
            return f'{each_part[0]}="{new_version}"{suffix}'
        else:
            return line
    else:
        return line


def get_latest_containers_version():
    res = requests.get("https://www.nationalarchives.gov.uk/pronom/container-signature.xml")
    last_modified_string = res.headers.get("Last-Modified")
    date = datetime.strptime(last_modified_string, '%a, %d %b %Y %H:%M:%S GMT')

    return datetime.strftime(date, '%Y%m%d')


def validate_xml(file_name):
    response = requests.get(f"https://cdn.nationalarchives.gov.uk/documents/{file_name}", timeout=10)
    try:
        ET.fromstring(response.content)
    except Exception:
        return False
    return True


if __name__ == '__main__':
    latest_droid_version = get_latest_droid_version()
    latest_containers_version = get_latest_containers_version()
    print(f"Latest DROID version: {latest_droid_version}")
    print(f"Latest Containers version: {latest_containers_version}")

    conf_path = "../../src/main/resources/application.conf"

    # Read file and update DROID and Containers versions
    with open(conf_path, "r") as conf:
        lines = conf.readlines()

    new_lines = list(lines)

    if validate_xml(f"DROID_SignatureFile_V{latest_droid_version}.xml"):
        new_lines = [replace_line(line, 'droid', latest_droid_version, "\n") for line in new_lines]

    if validate_xml(f"container-signature-{latest_containers_version}.xml"):
        new_lines = [replace_line(line, 'containers', latest_containers_version) for line in new_lines]

    # Only write back if something changed
    if new_lines != lines:
        with open(conf_path, "w") as conf:
            conf.writelines(new_lines)
