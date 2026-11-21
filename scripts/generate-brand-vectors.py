"""Export the editable brand geometry to Android XML and legacy SVG render inputs.

Existing attributed TV wordmark contours remain intact. No external artwork is introduced.
Render candidates with render-brand-icons.cjs, review them before copying to resources.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ANDROID = 'http://schemas.android.com/apk/res/android'
SVG = 'http://www.w3.org/2000/svg'
ET.register_namespace('android', ANDROID)
ET.register_namespace('aapt', 'http://schemas.android.com/aapt')
paths = list(ET.parse(ROOT / 'design/brand/mark.svg').getroot().findall(f'{{{SVG}}}path'))
attributes = {'d': 'pathData', 'fill': 'fillColor', 'stroke': 'strokeColor',
              'stroke-width': 'strokeWidth', 'stroke-linecap': 'strokeLineCap',
              'stroke-linejoin': 'strokeLineJoin'}


def mark(parent, monochrome=False):
    for path in paths:
        values = {f'{{{ANDROID}}}{attributes[key]}': ('#00000000' if value == 'none' else
                  '#FFFFFFFF' if monochrome and key in ('fill', 'stroke') else value)
                  for key, value in path.attrib.items()}
        ET.SubElement(parent, 'path', values)


def vector(name, monochrome=False, splash=False):
    drawable = ET.Element('vector', {f'{{{ANDROID}}}{key}': value for key, value in
        {'width': '108dp', 'height': '108dp', 'viewportWidth': '108', 'viewportHeight': '108'}.items()})
    group = ET.SubElement(drawable, 'group', {f'{{{ANDROID}}}{key}': value for key, value in
        {'pivotX': '54', 'pivotY': '54', 'scaleX': '1' if splash else '0.94',
         'scaleY': '1' if splash else '0.94'}.items()})
    mark(group, monochrome)
    ET.indent(drawable, space='    ')
    (RES / 'drawable' / name).write_text('<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated from design/brand/mark.svg by scripts/generate-brand-vectors.py. -->\n'
        + ET.tostring(drawable, encoding='unicode') + '\n', encoding='utf-8')


vector('ic_launcher_foreground.xml')
vector('ic_launcher_monochrome.xml', monochrome=True)
vector('splash_logo.xml', splash=True)

banner_path = RES / 'drawable/tv_banner_artwork.xml'
banner = ET.parse(banner_path).getroot()
for child in list(banner)[1:-1]:
    banner.remove(child)
group = ET.Element('group', {f'{{{ANDROID}}}{key}': value for key, value in
    {'translateX': '-9', 'translateY': '6', 'scaleX': '1.45', 'scaleY': '1.45'}.items()})
mark(group)
banner.insert(1, group)
ET.indent(banner, space='    ')
banner_path.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
    '<!-- Shared brand mark; existing wordmark uses attributed Roboto contours. -->\n'
    + ET.tostring(banner, encoding='unicode') + '\n', encoding='utf-8')

output = ROOT / 'build/branding'
output.mkdir(parents=True, exist_ok=True)
geometry = ''.join(ET.tostring(path, encoding='unicode').replace('ns0:', '').replace(':ns0', '') for path in paths)
for shape, background in [('square', '<rect width="108" height="108" rx="24" fill="#070A12"/>'),
                          ('round', '<circle cx="54" cy="54" r="54" fill="#070A12"/>')]:
    (output / f'launcher-{shape}.svg').write_text(
        f'<svg xmlns="{SVG}" width="108" height="108" viewBox="0 0 108 108">{background}'
        f'<g transform="translate(54 54) scale(1.3) translate(-54 -54)">{geometry}</g></svg>', encoding='utf-8')
print('Generated Android vector layers, splash, shared TV mark and legacy SVG render inputs')
