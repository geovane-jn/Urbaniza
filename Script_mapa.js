document.addEventListener('DOMContentLoaded', async () => {
    'use strict';
    const { api, ready, message, el, number, classification } = Urbaniza;
    if (!await ready) return;

    try {
        // A API substitui somente os bairros e as notas fixas do mapa original.
        const bairrosSP = (await api('/regioes')).map(b => ({ ...b, coords: [b.latitude, b.longitude] }));
        const normalizar = texto => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
        let categoriaAtiva = 'geral';
        let bairroSelecionado = bairrosSP.find(b => b.id === new URLSearchParams(location.search).get('regiao'))
            || bairrosSP.find(b => b.id === 'vila-mariana') || bairrosSP[0];

        const mapa = L.map('mapa-sp', { zoomControl: false }).setView([-23.592, -46.650], 12);
        const ruas = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(mapa);
        let erroDeRuas = false;
        ruas.on('tileerror', () => {
            if (!erroDeRuas) message('Não foi possível carregar as ruas. Verifique sua conexão; os limites e as avaliações continuam disponíveis.');
            erroDeRuas = true;
        });
        mapa.attributionControl.addAttribution('Distritos: <a href="https://github.com/codigourbano/distritos-sp">GeoSampa / Código Urbano</a>');
        setTimeout(() => mapa.invalidateSize(), 150);
        const camadaGeoJSON = L.layerGroup().addTo(mapa);
        const camadaMarcadores = L.layerGroup().addTo(mapa);

        function atualizarCardRegiao(bairro) {
            bairroSelecionado = bairro;
            document.getElementById('detalhe-regiao').hidden = !bairro;
            if (!bairro) return;
            const nota = bairro.notas[categoriaAtiva];
            const status = classification(nota);
            document.getElementById('nome-regiao').textContent = bairro.nome;
            document.getElementById('zona-regiao').textContent = bairro.zona;
            document.getElementById('nota-regiao').textContent = number(nota);
            const badge = document.getElementById('status-regiao');
            badge.textContent = status.text; badge.className = 'tag-status ' + status.css;
            const estrelas = document.getElementById('estrelas-regiao');
            estrelas.replaceChildren();
            for (let i = 1; i <= 5; i++) estrelas.append(el('span', '★', 'estrela ' + (nota != null && i <= Math.round(nota) ? 'ativa' : 'inativa')));
            estrelas.setAttribute('aria-label', nota == null ? 'Sem avaliações' : `Nota ${number(nota)} de 5`);
            document.getElementById('total-avaliacoes').textContent = bairro.cadastrado === false
                ? 'Distrito ainda não disponível para avaliação.'
                : `${bairro.totalAvaliacoes} avaliação(ões) · Categoria: ${document.querySelector(`[data-categoria="${categoriaAtiva}"]`).textContent.trim()}`;
            const avaliar = document.getElementById('avaliar-regiao');
            avaliar.hidden = bairro.cadastrado === false;
            avaliar.href = 'avaliacoes.html?regiao=' + encodeURIComponent(bairro.id);
        }
        function selecionar(bairro, mover = true) {
            atualizarCardRegiao(bairro);
            if (mover) mapa.panTo(bairro.coords);
            renderizarMarcadores();
        }
        function renderizarMarcadores() {
            const termo = normalizar(document.getElementById('input-busca-regiao').value.trim());
            const encontrados = bairrosSP.filter(b => normalizar(b.nome).includes(termo))
                .filter(b => termo || b.tipo !== 'BAIRRO' || b.id === bairroSelecionado?.id);
            camadaMarcadores.clearLayers();
            const lista = document.getElementById('lista-regioes'); lista.replaceChildren();
            if (!encontrados.length) lista.append(el('p', 'Nenhuma região encontrada. Tente outro nome.'));
            encontrados.forEach(bairro => {
                const { color } = classification(bairro.notas[categoriaAtiva]);
                const icone = el('div');
                const pino = el('div', '', 'pino-bairro' + (bairro.id === bairroSelecionado?.id ? ' selecionado' : ''));
                pino.style.backgroundColor = color;
                icone.append(pino, el('span', bairro.nome, 'nome-bairro-mapa'));
                L.marker(bairro.coords, {
                    title: bairro.nome, alt: bairro.nome,
                    icon: L.divIcon({ className: 'marcador-bairro-wrapper', html: icone, iconSize: [100, 46], iconAnchor: [50, 14] })
                }).addTo(camadaMarcadores).on('click', () => selecionar(bairro));
                const botao = el('button', `${bairro.nome} · ${number(bairro.notas[categoriaAtiva])} · ${bairro.totalAvaliacoes} avaliação(ões)`);
                botao.type = 'button'; botao.addEventListener('click', () => selecionar(bairro)); lista.append(botao);
            });
        }
        async function carregarDivisaoBairrosSP() {
            let geojson;
            try {
                const resposta = await fetch('https://raw.githubusercontent.com/codigourbano/distritos-sp/master/distritos-sp.geojson', { signal: AbortSignal.timeout(3000) });
                if (!resposta.ok) throw new Error('GeoJSON indisponível');
                geojson = await resposta.json();
            } catch {
                const resposta = await fetch('assets/distritos-sp.geojson');
                if (!resposta.ok) throw new Error('Não foi possível carregar os limites de São Paulo.');
                geojson = await resposta.json();
            }
            const estilo = { color: '#143456', weight: 1.2, opacity: 0.4, fillColor: '#143456', fillOpacity: 0.05 };
            L.geoJSON(geojson, {
                style: () => ({ ...estilo }),
                onEachFeature(feature, layer) {
                    const props = feature.properties || {};
                    const nome = props.ds_nome || props.NOME_DIST || props.name || props.nome || 'Região de SP';
                    layer.bindTooltip(nome);
                    layer.on({
                        mouseover: e => e.target.setStyle({ weight: 2.2, fillOpacity: 0.18, color: '#068a47' }),
                        mouseout: e => e.target.setStyle(estilo),
                        click: e => {
                            const existente = bairrosSP.find(b => b.tipo !== 'BAIRRO' && normalizar(b.nome) === normalizar(nome));
                            selecionar(existente || {
                                id: '', nome, zona: 'São Paulo • SP', cadastrado: false,
                                coords: [e.latlng.lat, e.latlng.lng], totalAvaliacoes: 0,
                                notas: { geral: null, seguranca: null, mobilidade: null, iluminacao: null }
                            }, false);
                        }
                    });
                }
            }).addTo(camadaGeoJSON);
        }
        document.querySelectorAll('.pilula-filtro').forEach(botao => botao.addEventListener('click', () => {
            categoriaAtiva = categoriaAtiva === botao.dataset.categoria ? 'geral' : botao.dataset.categoria;
            document.querySelectorAll('.pilula-filtro').forEach(b => {
                const ativo = b.dataset.categoria === categoriaAtiva;
                b.classList.toggle('selecionado', ativo); b.setAttribute('aria-pressed', String(ativo));
            });
            atualizarCardRegiao(bairroSelecionado); renderizarMarcadores();
        }));
        document.getElementById('input-busca-regiao').addEventListener('input', e => {
            const termo = normalizar(e.target.value.trim());
            const encontrado = bairrosSP.find(b => normalizar(b.nome) === termo && b.tipo !== 'BAIRRO')
                || bairrosSP.find(b => normalizar(b.nome).includes(termo));
            if (encontrado && termo.length >= 3) selecionar(encontrado);
            else renderizarMarcadores();
        });
        document.getElementById('btn-zoom-in').addEventListener('click', () => mapa.zoomIn());
        document.getElementById('btn-zoom-out').addEventListener('click', () => mapa.zoomOut());
        let localizacao;
        document.getElementById('btn-localizacao').addEventListener('click', () => {
            if (!navigator.geolocation) { message('Seu navegador não oferece geolocalização.', true); return; }
            mapa.locate({ setView: true, maxZoom: 14, timeout: 10000 });
        });
        mapa.on('locationfound', e => {
            if (localizacao) mapa.removeLayer(localizacao);
            localizacao = L.circleMarker(e.latlng, { color: '#2563eb', radius: 8 }).addTo(mapa).bindPopup('Sua localização').openPopup();
            message('Localização encontrada. Selecione um bairro disponível para avaliar.');
        });
        mapa.on('locationerror', () => message('Não foi possível obter sua localização. Verifique a permissão do navegador e use HTTPS ou localhost.', true));
        atualizarCardRegiao(bairroSelecionado); renderizarMarcadores();
        carregarDivisaoBairrosSP().catch(e => message(e.message, true));
    } catch (error) { message(error.message, true); }
});
