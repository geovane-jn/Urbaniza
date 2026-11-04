/* Fetch API centraliza o JWT e mantém erros visíveis nas telas. */
(() => {
  'use strict';
  const path = location.pathname.split('/').pop() || 'Index.html';
  const publicPages = ['Index.html', 'Cadastro.html', 'RecuperarSenha.html', 'VerificarCodigo.html', 'NovaSenha.html'];
  const $ = (selector, base = document) => base.querySelector(selector);
  const el = (tag, text, className) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; };
  function message(text, error = false, target) {
    let node = target || $('#status') || $('.mensagem');
    if (!node) { node = el('p', '', 'mensagem'); node.setAttribute('role', 'status'); node.setAttribute('aria-live', 'polite'); ($('main') || document.body).append(node); }
    node.textContent = text; node.classList.toggle('erro', error);
  }
  async function api(url, { method = 'GET', body } = {}) {
    const token = sessionStorage.getItem('urbaniza.token');
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try { response = await fetch('/api' + url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) }); }
    catch { throw new Error('Não foi possível conectar ao servidor. Confira sua conexão e tente novamente.'); }
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401 && !url.startsWith('/auth/') && !url.endsWith('/usuarios/me')) {
        sessionStorage.removeItem('urbaniza.token'); location.assign('Index.html');
      }
      const details = data.fields ? '\n' + Object.entries(data.fields).map(([key, value]) => `${key}: ${value}`).join('\n') : '';
      throw new Error((data.message || `Não foi possível concluir a operação (${response.status})`) + details);
    }
    return data;
  }
  function submit(form, handler) {
    form.addEventListener('submit', async event => {
      event.preventDefault(); const button = $('button[type="submit"]', form); if (button?.disabled) return;
      if (button) button.disabled = true;
      try { message(''); await handler(Object.fromEntries(new FormData(form))); }
      catch (error) { message(error.message, true); }
      finally { if (button) button.disabled = false; }
    });
  }
  function action(button, handler) {
    button.addEventListener('click', async () => {
      button.disabled = true;
      try { await handler(); } catch (error) { message(error.message, true); } finally { button.disabled = false; }
    });
  }
  function session(data) { sessionStorage.setItem('urbaniza.token', data.token); location.assign('inicio.html'); }
  const number = value => value == null ? 'Sem notas' : value.toLocaleString('pt-BR', { maximumFractionDigits: 1, minimumFractionDigits: 1 });
  function classification(value) {
    if (value == null) return { text: 'Sem avaliações', css: 'status-sem-notas', color: '#64748b' };
    if (value >= 4) return { text: 'Boa', css: 'status-boa', color: '#068a47' };
    if (value >= 3) return { text: 'Média', css: 'status-media', color: '#d97706' };
    return { text: 'Ruim', css: 'status-ruim', color: '#e11d48' };
  }
  async function authPages() {
    const form = $('form');
    if (path === 'Index.html') {
      submit(form, async data => session(await api('/auth/login', { method: 'POST', body: data })));
      if (sessionStorage.getItem('urbaniza.senhaAtualizada')) { message('Senha atualizada. Entre com sua nova senha.'); sessionStorage.removeItem('urbaniza.senhaAtualizada'); }
    }
    if (path === 'Cadastro.html') submit(form, async data => {
      if (data.senha !== data.confirmarSenha) throw new Error('As senhas não coincidem.');
      const { confirmarSenha, ...body } = data;
      session(await api('/auth/cadastro', { method: 'POST', body }));
    });
    if (path === 'RecuperarSenha.html') submit(form, async data => {
      sessionStorage.removeItem('urbaniza.resetToken'); sessionStorage.removeItem('urbaniza.devCode');
      const result = await api('/auth/recuperar', { method: 'POST', body: data });
      sessionStorage.setItem('urbaniza.resetEmail', data.email);
      if (result.codigoDesenvolvimento) sessionStorage.setItem('urbaniza.devCode', result.codigoDesenvolvimento);
      location.assign('VerificarCodigo.html');
    });
    if (path === 'VerificarCodigo.html') {
      const email = sessionStorage.getItem('urbaniza.resetEmail');
      if (!email) { location.replace('RecuperarSenha.html'); return; }
      const code = sessionStorage.getItem('urbaniza.devCode');
      if (code) { const note = el('p', `Modo de desenvolvimento: nenhum email foi enviado. Código de teste: ${code}`, 'aviso-local'); form.before(note); }
      else message('Se o email estiver cadastrado, o código foi enviado. Aguarde até 60 segundos antes de solicitar outro.');
      submit(form, async data => {
        const result = await api('/auth/verificar', { method: 'POST', body: { email, codigo: data.codigo } });
        sessionStorage.setItem('urbaniza.resetToken', result.token); sessionStorage.removeItem('urbaniza.devCode'); location.assign('NovaSenha.html');
      });
    }
    if (path === 'NovaSenha.html') {
      if (!sessionStorage.getItem('urbaniza.resetToken')) { location.replace('RecuperarSenha.html'); return; }
      submit(form, async data => {
        if (data.senha !== data.confirmarSenha) throw new Error('As senhas não coincidem.');
        await api('/auth/nova-senha', { method: 'POST', body: { token: sessionStorage.getItem('urbaniza.resetToken'), senha: data.senha } });
        ['urbaniza.resetEmail', 'urbaniza.resetToken', 'urbaniza.token'].forEach(key => sessionStorage.removeItem(key));
        sessionStorage.setItem('urbaniza.senhaAtualizada', '1'); location.assign('Index.html');
      });
    }
  }
  async function home() {
    const regions = await api('/regioes');
    const grid = $('#regioes-destaque'); grid.replaceChildren();
    regions.sort((a, b) => (b.notas.geral ?? -1) - (a.notas.geral ?? -1)).slice(0, 4).forEach(r => {
      const card = el('a', null, 'card-regiao'); card.href = `mapa.html?regiao=${encodeURIComponent(r.id)}`;
      const body = el('div', null, 'corpo-regiao'); const status = classification(r.notas.geral);
      body.append(el('h3', r.nome), el('p', r.zona, 'subtitulo-regiao'), el('p', `★ ${number(r.notas.geral)} · ${r.totalAvaliacoes} avaliação(ões)`), el('span', status.text, `tag-status ${status.css}`));
      card.append(body); grid.append(card);
    });
  }
  async function profile(user) {
    const form = $('#perfil-form');
    const editDialog = $('#editar-perfil');
    const fill = u => {
      $('#perfil-nome').textContent = u.nome; $('#perfil-email').textContent = u.email;
      $('#perfil-localidade').textContent = u.localidade;
      ['nome', 'username', 'localidade'].forEach(key => form.elements[key].value = u[key]);
    };
    fill(user); $('#admin-link').hidden = user.papel !== 'ADMIN';
    $('#abrir-edicao').addEventListener('click', event => { event.preventDefault(); message('', false, $('#editar-status')); editDialog.showModal(); });
    $('#cancelar-edicao-perfil').addEventListener('click', () => { fill(user); editDialog.close(); });
    submit(form, async data => {
      try { user = await api('/usuarios/me', { method: 'PUT', body: data }); fill(user); editDialog.close(); message('Perfil atualizado.'); }
      catch (error) { message(error.message, true, $('#editar-status')); }
    });
    action($('#sair'), async () => { await api('/auth/logout', { method: 'POST' }); sessionStorage.removeItem('urbaniza.token'); location.assign('Index.html'); });
    const dialog = $('#modal-excluir'); $('#abrir-exclusao').addEventListener('click', () => dialog.showModal());
    $('#cancelar-exclusao').addEventListener('click', () => dialog.close());
    // Erros de exclusão ficam dentro do diálogo, acessíveis durante a confirmação.
    const dialogStatus = el('p', '', 'mensagem'); dialogStatus.setAttribute('role', 'status'); dialog.append(dialogStatus);
    submit($('#excluir-conta-form'), async data => {
      try { await api('/usuarios/me', { method: 'DELETE', body: data }); sessionStorage.clear(); location.assign('Index.html'); }
      catch (error) { message(error.message, true, dialogStatus); }
    });
  }
  async function evaluations(user) {
    const regions = await api('/regioes');
    const form = $('#avaliacao-form'), filter = $('#filtro-avaliacoes');
    const regionSelect = form.elements.regiaoId;
    const search = $('#busca-bairro-avaliacao');
    const labelRegion = r => `${r.nome} — ${r.tipo === 'BAIRRO' ? r.zona : 'distrito'}`;
    const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    function options() {
      const previous = regionSelect.value;
      const term = normalize(search.value.trim());
      const matches = regions.filter(r => normalize(r.nome).includes(term));
      regionSelect.replaceChildren(new Option('Selecione o bairro', ''));
      matches.forEach(r => regionSelect.append(new Option(labelRegion(r), r.id)));
      if (matches.some(r => r.id === previous)) regionSelect.value = previous;
      else if (matches.length === 1) regionSelect.value = matches[0].id;
      $('#resultado-busca-bairro').textContent = matches.length ? `${matches.length} bairro(s)/distrito(s) encontrado(s).` : 'Nenhum bairro encontrado. Tente outro nome.';
    }
    options(); search.addEventListener('input', options);
    for (const r of regions) filter.elements.regiaoId.append(new Option(labelRegion(r), r.id));
    const initial = new URLSearchParams(location.search).get('regiao');
    if (regions.some(r => r.id === initial)) { regionSelect.value = initial; filter.elements.regiaoId.value = initial; }
    let editing = null, entries = [];
    function reset() { editing = null; form.reset(); search.disabled = false; options(); regionSelect.disabled = false; $('#cancelar-edicao').hidden = true; $('#form-titulo').textContent = 'Fazer avaliação'; $('button[type="submit"]', form).textContent = 'Publicar avaliação'; }
    $('#cancelar-edicao').addEventListener('click', reset);
    async function load() {
      const query = new URLSearchParams({ minhas: String(filter.elements.minhas.checked) });
      if (filter.elements.regiaoId.value) query.set('regiaoId', filter.elements.regiaoId.value);
      entries = await api('/avaliacoes?' + query); const list = $('#lista-avaliacoes'); list.replaceChildren();
      if (!entries.length) list.append(el('p', 'Nenhuma avaliação encontrada. Seja o primeiro a avaliar!'));
      entries.forEach(review => {
        const card = el('article'); card.append(el('h3', review.regiaoNome), el('p', `${review.nome} (@${review.username}) · ${new Date(review.atualizadoEm).toLocaleDateString('pt-BR')}`), el('p', `★ ${number(review.geral)} · Segurança ${review.seguranca} · Mobilidade ${review.mobilidade} · Iluminação ${review.iluminacao}`), el('p', review.comentario));
        if (review.usuarioId === user.id || user.papel === 'ADMIN') {
          const buttons = el('div', null, 'acoes'), edit = el('button', 'Editar'), remove = el('button', 'Excluir', 'perigo'); edit.type = remove.type = 'button';
          edit.addEventListener('click', () => {
            search.value = ''; options(); search.disabled = true;
            editing = review.id; ['regiaoId', 'seguranca', 'mobilidade', 'iluminacao', 'comentario'].forEach(key => form.elements[key].value = review[key]);
            regionSelect.disabled = true; $('#cancelar-edicao').hidden = false; $('#form-titulo').textContent = 'Editar avaliação'; $('button[type="submit"]', form).textContent = 'Salvar alterações'; form.scrollIntoView({ behavior: 'smooth', block: 'center' });
          });
          action(remove, async () => {
            if (!confirm('Excluir esta avaliação?')) return;
            await api('/avaliacoes/' + review.id, { method: 'DELETE' }); if (editing === review.id) reset(); await load(); message('Avaliação excluída.');
          });
          buttons.append(edit, remove); card.append(buttons);
        }
        list.append(card);
      });
    }
    submit(form, async data => {
      data.regiaoId = regionSelect.value;
      ['seguranca', 'mobilidade', 'iluminacao'].forEach(key => data[key] = Number(data[key]));
      await api('/avaliacoes' + (editing ? '/' + editing : ''), { method: editing ? 'PUT' : 'POST', body: data });
      reset(); await load(); message('Avaliação salva. As médias das regiões foram atualizadas.');
    });
    filter.addEventListener('change', () => load().catch(e => message(e.message, true)));
    filter.addEventListener('submit', e => e.preventDefault()); await load();
  }
  async function admin(user) {
    if (user.papel !== 'ADMIN') { message('Esta página é restrita a administradores.', true); $('#buscar-usuario').hidden = true; return; }
    const list = $('#lista-usuarios');
    function render(users) {
      list.replaceChildren();
      for (const u of users) {
        const card = el('article'); card.append(el('h2', u.nome), el('p', `@${u.username} · ${u.email}`), el('p', `Papel: ${u.papel}`));
        const actions = el('div', null, 'acoes'); const role = el('button', u.papel === 'ADMIN' ? 'Tornar usuário' : 'Tornar administrador'); const remove = el('button', 'Excluir usuário', 'perigo');
        action(role, async () => {
          if (!confirm(`Alterar o papel de @${u.username}? Suas sessões serão encerradas.`)) return;
          await api(`/admin/usuarios/${u.id}/papel`, { method: 'PUT', body: { papel: u.papel === 'ADMIN' ? 'USER' : 'ADMIN' } });
          if (u.id === user.id) { sessionStorage.removeItem('urbaniza.token'); location.assign('Index.html'); return; }
          await load(); message('Papel atualizado.');
        });
        action(remove, async () => {
          if (!confirm(`Excluir @${u.username} e suas avaliações definitivamente?`)) return;
          await api('/admin/usuarios/' + u.id, { method: 'DELETE' });
          if (u.id === user.id) { sessionStorage.removeItem('urbaniza.token'); location.assign('Index.html'); return; }
          await load(); message('Usuário excluído.');
        });
        actions.append(role, remove); card.append(actions); list.append(card);
      }
    }
    async function load() { render(await api('/admin/usuarios')); }
    submit($('#buscar-usuario'), async data => render([await api('/usuarios/' + encodeURIComponent(data.username))]));
    action($('#listar-usuarios'), load); await load();
  }
  let resolveReady;
  const ready = new Promise(resolve => { resolveReady = resolve; });
  window.Urbaniza = { api, message, el, number, classification, ready };
  document.addEventListener('DOMContentLoaded', async () => {
    document.querySelectorAll('.mostrar-senha').forEach(button => button.addEventListener('click', () => {
      const input = $('input', button.closest('.campo')); const show = input.type === 'password'; input.type = show ? 'text' : 'password'; button.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha'); button.setAttribute('aria-pressed', String(show));
    }));
    try {
      if (publicPages.includes(path)) { await authPages(); resolveReady(null); return; }
      if (!sessionStorage.getItem('urbaniza.token')) { location.replace('Index.html'); resolveReady(null); return; }
      let user;
      try { user = await api('/usuarios/me'); }
      catch (error) { sessionStorage.removeItem('urbaniza.token'); location.replace('Index.html'); resolveReady(null); return; }
      resolveReady(user);
      if (path === 'inicio.html') await home();
      if (path === 'perfil.html') await profile(user);
      if (path === 'avaliacoes.html') await evaluations(user);
      if (path === 'admin.html') await admin(user);
    } catch (error) { resolveReady(null); message(error.message, true); }
  });
})();
