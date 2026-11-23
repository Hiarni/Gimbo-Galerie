#!/usr/bin/env python3

from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'

def read(folder):
    result = {}
    for entry in ET.parse(res / folder / 'strings.xml').getroot():
        if entry.get('translatable') == 'false':
            continue
        key = entry.attrib['name']
        assert key not in result, f'Duplicate resource: {folder}/{key}'
        if entry.tag == 'plurals':
            value = {item.attrib['quantity']: item.text or '' for item in entry}
            assert {'one', 'other'} <= value.keys(), f'Missing plural form: {folder}/{key}'
        else:
            assert entry.tag == 'string', f'Unexpected resource: {key}'
            value = {'string': entry.text or ''}
        assert all(text.strip() for text in value.values()), f'Empty resource: {folder}/{key}'
        result[key] = value
    return result

def arguments(text):
    return sorted(re.findall(r'%([1-9][0-9]*)\$[-+ #0,(]*\d*(?:\.\d+)?([a-zA-Z])', text))

english, german = read('values'), read('values-de')
assert english.keys() == german.keys(), f'Missing translations: {english.keys() ^ german.keys()}'
for key in english:
    assert english[key].keys() == german[key].keys(), f'Mismatched resource type or plural forms: {key}'
    for form in english[key]:
        assert arguments(english[key][form]) == arguments(german[key][form]), f'Mismatched format arguments: {key}/{form}'
        text = re.sub(r'%[1-9][0-9]*\$[-+ #0,(]*\d*(?:\.\d+)?[a-zA-Z]', '', english[key][form] + german[key][form])
        assert not re.search(r'\$\w|\$\{', text), f'Unconverted Kotlin interpolation: {key}'
locales = ET.parse(res / 'xml/locales_config.xml').getroot()
android_name = '{http://schemas.android.com/apk/res/android}name'
supported={entry.get(android_name) for entry in locales}
assert {'de','en'} <= supported and len(supported)==33, 'Unexpected supported languages'
for tag in supported-{'de','en'}:
    folder='values-'+('b+'+tag.replace('-','+') if '-' in tag else tag)
    translated=read(folder)
    assert translated.keys() <= english.keys(), f'Unknown translations: {tag}'
    for key,forms in translated.items():
        for form,text in forms.items():
            assert arguments(text)==arguments(english[key][form]), f'Mismatched format arguments: {tag}/{key}'
    assert {'photos','videos','albums','delete','cancel','sort_media'} <= translated.keys(), f'Incomplete core navigation: {tag}'
print('OK: 33 packaged locales; additional languages provide core navigation with explicit English fallback.')
assert english['photos']['string'] == 'Photos' and german['photos']['string'] == 'Fotos'
assert 'enableSplit = false' in (root / 'app/build.gradle.kts').read_text(), 'Language bundles must be included offline'
print(f'OK: {len(english)} fully translated German/English resources; format arguments, plurals and offline packaging match.')
