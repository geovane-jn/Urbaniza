#!/usr/bin/env python3
"""Gera o catálogo de distritos e nomes locais de bairros do OpenStreetMap.
Uso: python3 scripts/importar-bairros.py /caminho/osm_bairros_e_distritos.geojson
Fonte ODbL fixada: https://raw.githubusercontent.com/vmvaz1935/bairros-sp/44dba1025c7dafaeaddd3e8e0a3de80b2c4ee936/dados/osm_bairros_e_distritos.geojson
O script processa dados; não executa código da fonte nem acessa a rede.
"""
import json,unicodedata,re,pathlib,sys,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
names='''Água Rasa|Alto de Pinheiros|Anhanguera|Aricanduva|Artur Alvim|Barra Funda|Bela Vista|Belém|Bom Retiro|Brás|Brasilândia|Butantã|Cachoeirinha|Cambuci|Campo Belo|Campo Grande|Campo Limpo|Cangaíba|Capão Redondo|Carrão|Casa Verde|Cidade Ademar|Cidade Dutra|Cidade Líder|Cidade Tiradentes|Consolação|Cursino|Ermelino Matarazzo|Freguesia do Ó|Grajaú|Guaianases|Iguatemi|Ipiranga|Itaim Bibi|Itaim Paulista|Itaquera|Jabaquara|Jaçanã|Jaguara|Jaguaré|Jaraguá|Jardim Ângela|Jardim Helena|Jardim Paulista|Jardim São Luís|José Bonifácio|Lajeado|Lapa|Liberdade|Limão|Mandaqui|Marsilac|Moema|Mooca|Morumbi|Parelheiros|Pari|Parque do Carmo|Pedreira|Penha|Perdizes|Perus|Pinheiros|Pirituba|Ponte Rasa|Raposo Tavares|República|Rio Pequeno|Sacomã|Santa Cecília|Santana|Santo Amaro|São Domingos|São Lucas|São Mateus|São Miguel|São Rafael|Sapopemba|Saúde|Sé|Socorro|Tatuapé|Tremembé|Tucuruvi|Vila Andrade|Vila Curuçá|Vila Formosa|Vila Guilherme|Vila Jacuí|Vila Leopoldina|Vila Maria|Vila Mariana|Vila Matilde|Vila Medeiros|Vila Prudente|Vila Sônia'''.split('|')
def slug(s):return re.sub('[^a-z0-9]+','-',unicodedata.normalize('NFKD',s).encode('ascii','ignore').decode().lower()).strip('-')
display={slug(n):n for n in names}
features=json.loads((root/'assets/distritos-sp.geojson').read_text())['features']
def ring_area(r):return sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(r,r[1:]))/2

def inside(r,x,y):
 return sum(1 for a,b in zip(r,r[1:]) if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])%2==1

def point(geom):
 polygons=[geom['coordinates']] if geom['type']=='Polygon' else geom['coordinates']
 poly=max(polygons,key=lambda p:abs(ring_area(p[0])))
 r=poly[0]; area=ring_area(r)
 x=sum((a[0]+b[0])*(a[0]*b[1]-b[0]*a[1]) for a,b in zip(r,r[1:]))/(6*area)
 y=sum((a[1]+b[1])*(a[0]*b[1]-b[0]*a[1]) for a,b in zip(r,r[1:]))/(6*area)
 if not inside(r,x,y) or any(inside(h,x,y) for h in poly[1:]):
  y=(min(v[1] for v in r)+max(v[1] for v in r))/2
  hits=sorted(a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]) for ring in poly for a,b in zip(ring,ring[1:]) if (a[1]>y)!=(b[1]>y))
  left,right=max(zip(hits[::2],hits[1::2]),key=lambda pair:pair[1]-pair[0]);x=(left+right)/2
 assert inside(r,x,y) and not any(inside(h,x,y) for h in poly[1:])
 return round(y,6),round(x,6)
items=[]
for f in features:
 id=slug(f['properties']['nome']); name=display[id];lat,lon=point(f['geometry'])
 items.append(dict(id=id,nome=name,zona='Distrito • São Paulo',latitude=lat,longitude=lon,tipo='DISTRITO',distritoId=id))
assert len(items)==len({r['id'] for r in items})==96
assert max(len(r['id']) for r in items)<=40
# Geometria oficial: usada para associar cada nome local a um distrito.
districts=[]
for feature in features:
    geometry=feature['geometry']
    polygons=[geometry['coordinates']] if geometry['type']=='Polygon' else geometry['coordinates']
    boxes=[(min(v[0] for v in p[0]),min(v[1] for v in p[0]),max(v[0] for v in p[0]),max(v[1] for v in p[0]),p) for p in polygons]
    districts.append((slug(feature['properties']['nome']),boxes))
def district_at(x,y):
    for id,boxes in districts:
        for left,bottom,right,top,polygon in boxes:
            if left<=x<=right and bottom<=y<=top and inside(polygon[0],x,y) and not any(inside(h,x,y) for h in polygon[1:]):return id
    return None
raw=pathlib.Path(sys.argv[1]).read_bytes()
assert hashlib.sha256(raw).hexdigest()=='721071560aa6cdfd824540492f35ce54fe61b83f12631dc9922270aa5e1b7d45', 'Use o arquivo da versão documentada em assets/FONTES.md'
osm=json.loads(raw)['features']
# Pontos são priorizados; polígonos acrescentam nomes que ainda não têm ponto no mesmo distrito.
osm.sort(key=lambda f:f['geometry']['type']!='Point')
seen=set(); outside=[]; ids={r['id'] for r in items}
for feature in osm:
    props=feature['properties']
    if props.get('layer')=='distrito':continue
    name=props['name'].strip()
    if feature['geometry']['type']=='Point':lon,lat=feature['geometry']['coordinates']
    else:lat,lon=point(feature['geometry'])
    district=district_at(lon,lat)
    if not district:
        outside.append(name);continue
    if slug(name)==district:continue # Nome do próprio distrito já cadastrado.
    identity=(slug(name),district)
    if identity in seen:continue
    seen.add(identity)
    id='bairro-'+slug(name)+'-'+district
    if len(id)>40:id=id[:31]+'-'+hashlib.sha256(id.encode()).hexdigest()[:8]
    assert id not in ids and 0<len(name)<=100
    ids.add(id)
    items.append(dict(id=id,nome=name,zona='Distrito de '+display[district]+' • São Paulo',latitude=round(lat,6),longitude=round(lon,6),tipo='BAIRRO',distritoId=district))
assert len(items)==len({r['id'] for r in items})
assert max(len(r['id']) for r in items)<=40
(root/'src/main/resources/regioes-sp.json').write_text(json.dumps(sorted(items,key=lambda x:x['id']),ensure_ascii=False,indent=2)+'\n')
print('Catálogo:',len(items),'regiões;',len(items)-96,'bairros locais e 96 distritos')
print('Localidades fora do limite municipal:',outside)
for r in items:
    if 'santo antonio' in slug(r['nome']).replace('-',' ') or 'madalena' in slug(r['nome']):print(r)
