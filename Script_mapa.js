document.addEventListener('DOMContentLoaded', async () => {
  'use strict';
  const { api, ready, message, el, number, classification } = Urbaniza;
  if (!await ready) return;
  try {
    const regions = await api('/regioes');
    let selected = regions.find(r => r.id === new URLSearchParams(location.search).get('regiao')) || regions[0];
    let category = 'geral';
    const map = L.map('mapa-sp', { zoomControl: false }).setView([-23.592, -46.650], 12);
    map.attributionControl.addAttribution('Distritos: <a href="https://github.com/codigourbano/distritos-sp">GeoSampa / Código Urbano</a>');
    const markers = L.layerGroup().addTo(map);
    const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    // Base geográfica incluída no projeto: nenhuma chave ou conexão externa é necessária.
    const response = await fetch('assets/distritos-sp.geojson');
    if (!response.ok) throw new Error('Não foi possível carregar a base geográfica. Recarregue a página.');
    const districts = await response.json();
    L.geoJSON(districts, {
      style: { color: '#8195a9', weight: 1, fillColor: '#d6e8dc', fillOpacity: .7 },
      onEachFeature(feature, layer) {
        layer.bindTooltip(feature.properties.nome);
        layer.on('click', () => {
          const region = regions.find(r => normalize(r.nome) === normalize(feature.properties.nome));
          if (region) select(region);
          else message(`${feature.properties.nome}: distrito ainda sem ponto de avaliação cadastrado. Escolha um dos bairros disponíveis.`);
        });
      }
    }).addTo(map);
    function detail() {
      document.getElementById('detalhe-regiao').hidden = !selected;
      if (!selected) return;
      const rating = selected.notas[category], status = classification(rating);
      document.getElementById('nome-regiao').textContent = selected.nome;
      document.getElementById('zona-regiao').textContent = selected.zona;
      document.getElementById('nota-regiao').textContent = number(rating);
      const badge = document.getElementById('status-regiao'); badge.textContent = status.text; badge.className = 'tag-status ' + status.css;
      document.getElementById('total-avaliacoes').textContent = `${selected.totalAvaliacoes} avaliação(ões) · Categoria: ${document.querySelector(`[data-categoria="${category}"]`).textContent}`;
      document.getElementById('avaliar-regiao').href = 'avaliacoes.html?regiao=' + encodeURIComponent(selected.id);
    }
    function select(region) { selected = region; detail(); map.panTo([region.latitude, region.longitude]); render(); }
    function render() {
      const term = normalize(document.getElementById('input-busca-regiao').value.trim());
      const matches = regions.filter(r => normalize(r.nome).includes(term));
      const list = document.getElementById('lista-regioes'); list.replaceChildren(); markers.clearLayers();
      if (!matches.length) list.append(el('p', 'Nenhuma região encontrada. Tente outro nome.'));
      for (const r of matches) {
        const status = classification(r.notas[category]);
        const pin = el('div', '', 'pino-bairro' + (r.id === selected?.id ? ' selecionado' : '')); pin.style.backgroundColor = status.color;
        const icon = L.divIcon({ html: pin, className: 'marcador-bairro-wrapper', iconSize: [26, 26] });
        L.marker([r.latitude, r.longitude], { icon, title: r.nome, alt: r.nome }).addTo(markers).on('click', () => select(r));
        const button = el('button', `${r.nome} · ${number(r.notas[category])} · ${r.totalAvaliacoes} avaliação(ões)`);
        button.type = 'button'; button.addEventListener('click', () => select(r)); list.append(button);
      }
    }
    document.getElementById('input-busca-regiao').addEventListener('input', () => {
      render(); const term = normalize(document.getElementById('input-busca-regiao').value.trim());
      const found = regions.find(r => normalize(r.nome).includes(term)); if (found && term.length >= 3) select(found);
    });
    document.querySelectorAll('.pilula-filtro').forEach(button => button.addEventListener('click', () => {
      category = button.dataset.categoria;
      document.querySelectorAll('.pilula-filtro').forEach(b => { b.classList.toggle('selecionado', b === button); b.setAttribute('aria-pressed', String(b === button)); }); detail(); render();
    }));
    document.getElementById('btn-zoom-in').addEventListener('click', () => map.zoomIn());
    document.getElementById('btn-zoom-out').addEventListener('click', () => map.zoomOut());
    let locationMarker;
    document.getElementById('btn-localizacao').addEventListener('click', () => {
      if (!navigator.geolocation) { message('Seu navegador não oferece geolocalização.', true); return; }
      map.locate({ setView: true, maxZoom: 14, timeout: 10000 });
    });
    map.on('locationfound', event => {
      if (locationMarker) map.removeLayer(locationMarker);
      locationMarker = L.circleMarker(event.latlng, { color: '#2563eb', radius: 8 }).addTo(map).bindPopup('Sua localização').openPopup();
      message('Localização encontrada. Selecione um bairro disponível para avaliar.');
    });
    map.on('locationerror', () => message('Não foi possível obter sua localização. Verifique a permissão do navegador e use HTTPS ou localhost.', true));
    detail(); render();
  } catch (error) { message(error.message, true); }
});
