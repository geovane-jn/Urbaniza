# Recursos externos incluídos

- `leaflet/`: Leaflet 1.9.4, licença BSD-2-Clause em `leaflet/LICENSE`. Distribuição do registro npm, verificada pelo SHA-512 do pacote antes da extração.
- `distritos-sp.geojson`: limites dos 96 distritos de São Paulo, fonte GeoSampa / Secretaria Municipal do Desenvolvimento Urbano, disponibilizados por Código Urbano: https://github.com/codigourbano/distritos-sp/blob/master/distritos-sp.geojson . Coordenadas arredondadas a cinco casas decimais e propriedades reduzidas ao nome do distrito para distribuição local. Não representa ruas, endereços ou limites precisos de bairros; os marcadores indicam pontos de referência dos bairros disponíveis.

A biblioteca Leaflet é servida localmente. As imagens de ruas são fornecidas por OpenStreetMap (`https://tile.openstreetmap.org`), com atribuição no mapa, e exigem internet. Os limites são carregados do GeoJSON original, com a cópia local como alternativa. Não é necessária chave de mapas.

## Nomes locais de bairros

`src/main/resources/regioes-sp.json` reúne **96 distritos e 517 localidades de bairro**. Os nomes locais vêm da extração comunitária do OpenStreetMap em [vmvaz1935/bairros-sp](https://github.com/vmvaz1935/bairros-sp), arquivo `dados/osm_bairros_e_distritos.geojson`, versão `44dba1025c7dafaeaddd3e8e0a3de80b2c4ee936`.

Este catálogo usa a extração OSM, **não** a base de 1.854 bairros postais do mesmo repositório. A estimativa de 472 bairros mencionada durante o desenvolvimento não corresponde a uma lista verificada nestas fontes. A aplicação mantém os nomes encontrados na fonte, sem cortar registros para atingir essa contagem. A cobertura comunitária não é um cadastro oficial nem uma garantia de incluir todos os nomes informais.

Processamento: nomes de distritos são preservados; pontos de bairro têm prioridade sobre polígonos; nomes iguais no mesmo distrito são unificados; nomes homônimos em distritos diferentes permanecem separados. A associação ao distrito é calculada pela localização dentro do GeoJSON GeoSampa. Todos os pontos importados ficaram dentro do limite municipal. Exemplo: **Chácara Santo Antônio → distrito de Santo Amaro**, com avaliação própria. Pontos são referências geográficas; não definem limites oficiais de bairros.

© colaboradores do [OpenStreetMap](https://www.openstreetmap.org/copyright). A base derivada de nomes e coordenadas é redistribuída sob a [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/). O código da aplicação é separado da licença dos dados.

Para reproduzir: baixe o arquivo da versão indicada e execute `python3 scripts/importar-bairros.py /caminho/osm_bairros_e_distritos.geojson`. O script usa apenas Python padrão, confere o SHA-256 de entrada (`721071560aa6cdfd824540492f35ce54fe61b83f12631dc9922270aa5e1b7d45`), as coordenadas e a unicidade dos IDs. A importação em cada início do aplicativo acrescenta somente os registros ausentes, sem apagar avaliações anteriores.
