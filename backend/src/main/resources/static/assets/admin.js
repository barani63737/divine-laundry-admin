'use strict';
document.querySelectorAll('.sidebar nav a').forEach(a => {
  if (a.getAttribute('href') === location.pathname) a.classList.add('current');
});
document.querySelectorAll('[data-print]').forEach(button => button.addEventListener('click', () => window.print()));
document.querySelectorAll('[data-lock-submit]').forEach(form => {
  form.addEventListener('submit', event => {
    const message = form.dataset.confirm;
    if (message && !window.confirm(message)) event.preventDefault();
  });
  form.addEventListener('submit', event => {
    if (event.defaultPrevented) return;
    if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
    form.dataset.submitting = 'true';
    form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = true; });
  });
});
const passwordToggle = document.querySelector('[data-password-toggle]');
if (passwordToggle) {
  passwordToggle.addEventListener('click', () => {
    const password = document.getElementById(passwordToggle.getAttribute('aria-controls'));
    const visible = password.type === 'text';
    password.type = visible ? 'password' : 'text';
    passwordToggle.textContent = visible ? 'Show' : 'Hide';
    passwordToggle.setAttribute('aria-label', visible ? 'Show password' : 'Hide password');
  });
}
const loginForm = document.querySelector('[data-login-form]');
if (loginForm) {
  loginForm.addEventListener('submit', () => {
    const submit = loginForm.querySelector('[data-login-submit]');
    submit.disabled = true;
    submit.querySelector('[data-submit-label]').textContent = 'Signing in...';
  });
}
const dashboardMenu = document.querySelector('[data-dashboard-menu]');
const dashboardClose = document.querySelector('[data-dashboard-close]');
const dashboardScrim = document.querySelector('[data-dashboard-scrim]');
const dashboardSidebar = document.getElementById('dashboard-navigation');
const setDashboardNavigation = open => {
  if (!dashboardSidebar || !dashboardMenu) return;
  dashboardSidebar.classList.toggle('is-open', open);
  dashboardScrim?.classList.toggle('is-visible', open);
  dashboardMenu.setAttribute('aria-expanded', String(open));
};
dashboardMenu?.addEventListener('click', () => setDashboardNavigation(true));
dashboardClose?.addEventListener('click', () => setDashboardNavigation(false));
dashboardScrim?.addEventListener('click', () => setDashboardNavigation(false));
// Restore submission controls after browser Back/bfcache without changing request IDs.
window.addEventListener('pageshow', () => {
  document.querySelectorAll('[data-lock-submit]').forEach(form => {
    if (form.dataset.submitting === 'true') {
      delete form.dataset.submitting;
      form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = false; });
    }
  });
});

const paymentRequestForm = document.getElementById('online-payment-request-form');
if (paymentRequestForm) {
  const feedback = document.getElementById('payment-request-feedback');
  const list = document.getElementById('payment-request-list');
  const orderNumber = location.pathname.split('/').filter(Boolean).pop();
  const amount = paymentRequestForm.elements.amount;
  const submit = paymentRequestForm.querySelector('button[type="submit"]');
  if (submit) submit.textContent = 'Request payment via WhatsApp';
  const renderRequests = requests => {
    if (!list) return;
    list.innerHTML = requests.map(request => `<li><strong>${request.status}</strong> ₹${request.requestedAmount}`
      + (request.paymentUrl ? ` <a href="${request.paymentUrl}" target="_blank" rel="noreferrer">Payment link</a>` : '')
      + '</li>').join('');
  };
  fetch(`/api/orders/${encodeURIComponent(orderNumber)}/payment-requests`)
    .then(response => response.ok ? response.json() : [])
    .then(renderRequests)
    .catch(() => {});
  paymentRequestForm.addEventListener('submit', event => {
    event.preventDefault();
    if (!amount.value || Number(amount.value) <= 0) return;
    submit.disabled = true;
    fetch(`/api/orders/${encodeURIComponent(orderNumber)}/payment-requests`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ amount: amount.value, idempotencyKey: paymentRequestForm.elements.idempotencyKey.value })
    }).then(async response => {
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || body.error || 'Unable to create payment request');
      if (feedback) feedback.textContent = 'Payment request created. WhatsApp delivery will follow after the payment link is saved.';
      renderRequests([body]);
    }).catch(error => { if (feedback) feedback.textContent = error.message; })
      .finally(() => { submit.disabled = false; });
  });
}
const orderForm = document.getElementById('order-form');
if (orderForm) {
  const body = document.getElementById('line-items');
  orderForm.addEventListener('submit', () => {
    const label = orderForm.querySelector('[data-order-submit-label]');
    if (label) label.textContent = 'Creating order...';
  });
  const format = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);
  const imageByCode = {
    SHIRT_DRY: 'shirt.svg', TSHIRT_DRY_M: 'tshirt.svg', TSHIRT_DRY_W: 'tshirt.svg', TSHIRT_DRY_K: 'tshirt.svg',
    VEST_DRY_M: 'vest.svg', UNDERWEAR_DRY_M: 'underwear.svg', PULLOVER_DRY_M: 'pullover.svg', PULLOVER_DRY_W: 'pullover.svg', PULLOVER_DRY_K: 'pullover.svg',
    SWEATPANT_DRY_M: 'sweat-pants.svg', CAPRI_DRY_M: 'capri.svg', CAPRI_DRY_W: 'capri.svg', CAPRI_DRY_K: 'capri.svg',
    PYJAMA_DRY_M: 'pyjama.svg', TRACKPANT_DRY_M: 'track-pant.svg', TRACKPANT_DRY_W: 'track-pant.svg', TRACKPANT_DRY_K: 'track-pant.svg',
    SHORTS_DRY_M: 'shorts.svg', SHORTS_DRY_K: 'shorts.svg', LONGCOAT_DRY_M: 'long-coat.svg', LONGCOAT_DRY_W: 'long-coat.svg',
    BLAZER_DRY_M: 'blazer.svg', BLAZER_DRY_W: 'blazer.svg', JEANS_DRY_M: 'jeans.svg', JEANS_DRY_W: 'jeans.svg', JEANS_DRY_K: 'jeans.svg',
    TIE_DRY_M: 'tie.svg', TIE_DRY_A: 'tie.svg', SHERWANI_DRY_M: 'sherwani.svg', SHERWANI_DRY_K: 'sherwani.svg',
    ACHKAN_DRY_M: 'blazer.svg', DANGREE_DRY_W: 'dress.svg', DANGREE_DRY_K: 'dress.svg', JUMPER_DRY_W: 'sweater.svg', JUMPER_DRY_K: 'sweater.svg',
    HANDBAG_DRY_H: 'bag.svg', BABYBLANKET_DRY_K: 'blanket.svg',
    SHIRT_IRON: 'shirt.svg', TSHIRT_IRON: 'tshirt.svg', PANT_IRON: 'trousers.svg', LONG_DRESS_IRON: 'dress.svg',
    PILLOW_COVER_IRON: 'bedsheet.svg', COAT_BLAZER_IRON: 'blazer.svg', OVERCOAT_IRON: 'long-coat.svg',
    TABLERUNNER_DRY_H: 'bedsheet.svg', FOOTMAT_DRY_H: 'bedsheet.svg', TABLEMAT_DRY_H: 'bedsheet.svg', BATHROBE_DRY_H: 'blanket.svg',
    SOCKS_DRY_H: 'trousers.svg', HANDKERCHIEF_DRY_A: 'default-laundry.svg', RAINCOAT_DRY_A: 'long-coat.svg',
    WASH_IRON_KG: 'washing.svg', WASH_FOLD_KG: 'washing.svg', WASH_FOLD_EXPRESS_KG: 'washing.svg', WASH_IRON_EXPRESS_KG: 'washing.svg',
    SPORT_SHOE: 'shoe.svg', CANVAS_SHOE: 'shoe.svg', LEATHER_SHOE: 'shoe.svg', SUEDE_SHOE: 'shoe.svg', CROCS_SANDALS: 'shoe.svg', SLIPPERS: 'shoe.svg',
    SOFA_SEAT: 'sofa.svg'
  };
  const imageFor = (name = '', category = '', code = '') => {
    const value = `${name} ${category} ${code}`.toLowerCase();
    if (imageByCode[code]) return `/assets/service-images/${imageByCode[code]}`;
    if (value.includes('sofa') || value.includes('upholstery')) return '/assets/service-images/sofa.svg';
    if (value.includes('shoe')) return '/assets/service-images/shoe.svg';
    if (value.includes('wash') || value.includes('kg')) return '/assets/service-images/washing.svg';
    if (value.includes('iron')) return '/assets/service-images/iron.svg';
    if (value.includes('blanket')) return '/assets/service-images/blanket.svg';
    if (value.includes('curtain')) return '/assets/service-images/curtain.svg';
    if (value.includes('bag') || value.includes('handbag')) return '/assets/service-images/bag.svg';
    if (value.includes('jean')) return '/assets/service-images/jeans.svg';
    if (value.includes('blazer') || value.includes('coat') || value.includes('achkan') || value.includes('sherwani')) return '/assets/service-images/blazer.svg';
    if (value.includes('bed') || value.includes('bedsheet') || value.includes('duvet') || value.includes('quilt')) return '/assets/service-images/bedsheet.svg';
    if (value.includes('saree') || value.includes('silk') || value.includes('dress')) return '/assets/service-images/saree.svg';
    if (value.includes('t-shirt') || value.includes('tshirt')) return '/assets/service-images/tshirt.svg';
    if (value.includes('pant') || value.includes('trouser')) return '/assets/service-images/trousers.svg';
    if (value.includes('shirt') || value.includes('vest') || value.includes('pullover') || value.includes('jumper')) return '/assets/service-images/shirt.svg';
    return '/assets/service-images/default-laundry.svg';
  };
  const catalog = document.getElementById('service-catalog');
  const dryCatalog = document.getElementById('dry-service-catalog');
  const directCatalog = document.getElementById('direct-service-catalog');
  const search = document.getElementById('service-search');
  const customerSearch = document.getElementById('customer-search');
  const customerSelect = orderForm.elements.customerId;
  const catalogCount = document.getElementById('catalog-count');
  const catalogEmpty = document.getElementById('catalog-empty');
  let selectedCategory = 'Dry Clean';
  let selectedGroup = 'All';
  const categoryCopy = {
    'Dry Clean': ['DRY CLEAN', 'Every garment, handled with care.', 'Choose a group or search to find a service quickly.', '/assets/service-images/shirt.svg'],
    'Laundry by KG': ['LAUNDRY BY KG', 'Fresh & clean always.', 'Wash, fold, and express care priced by the kilogram.', '/assets/service-images/washing.svg'],
    Ironing: ['IRONING', 'Pressed to perfection.', 'Crisp finishing for everyday wear and special pieces.', '/assets/service-images/iron.svg'],
    'Shoe Cleaning': ['SHOE CLEANING', 'Step out fresh.', 'Specialist care for every pair.', '/assets/service-images/shoe.svg'],
    'Sofa Cleaning': ['SOFA CLEANING', 'A cleaner place to relax.', 'Refresh upholstery and make every seat feel new.', '/assets/service-images/sofa.svg']
  };
  const renderCatalog = () => {
    const query = search.value.trim().toLowerCase();
    let visible = 0;
    const isDryClean = selectedCategory === 'Dry Clean';
    dryCatalog.hidden = !isDryClean;
    directCatalog.hidden = isDryClean;
    dryCatalog.querySelectorAll('.service-group').forEach(group => {
      let groupVisible = 0;
      group.querySelectorAll('.service-card').forEach(card => {
        const matchesGroup = selectedGroup === 'All' || card.dataset.serviceGroup === selectedGroup;
        const text = `${card.dataset.serviceName} ${card.dataset.serviceCode || ''} ${card.dataset.serviceGroup || ''} ${card.dataset.serviceCategory}`.toLowerCase();
        const matchesSearch = !query || text.includes(query);
        const show = matchesGroup && matchesSearch;
        card.hidden = !show;
        if (show) { groupVisible += 1; visible += 1; }
      });
      group.hidden = groupVisible === 0;
    });
    directCatalog.querySelectorAll('.service-card').forEach(card => {
      const text = `${card.dataset.serviceName} ${card.dataset.serviceCode || ''} ${card.dataset.serviceCategory}`.toLowerCase();
      const show = card.dataset.serviceCategory === selectedCategory && (!query || text.includes(query));
      card.hidden = !show;
      if (show) visible += 1;
    });
    catalogCount.textContent = `${visible} service${visible === 1 ? '' : 's'}`;
    catalogEmpty.hidden = visible !== 0;
    document.getElementById('dry-filter-bar').hidden = selectedCategory !== 'Dry Clean';
    const copy = categoryCopy[selectedCategory];
    document.getElementById('catalog-banner-label').textContent = copy[0];
    document.getElementById('catalog-banner-title').textContent = copy[1];
    document.getElementById('catalog-banner-copy').textContent = copy[2];
    document.getElementById('catalog-banner-image').src = copy[3];
  };
  const syncLine = row => {
    const select = row.querySelector('[data-service]');
    const option = select?.selectedOptions[0];
    const name = option?.dataset.name || '';
    row.querySelector('[data-line-name]').textContent = name || 'Select a service';
    row.querySelector('[data-line-meta]').textContent = name ? `${option.dataset.code} · ${option.dataset.category} · ₹${option.dataset.rate} / ${option.dataset.unit}` : 'Choose from the service menu';
    const fallback = '/assets/service-images/default-laundry.svg';
    const lineImage = row.querySelector('[data-line-image]');
    lineImage.onerror = () => { lineImage.onerror = null; lineImage.src = fallback; };
    lineImage.src = imageFor(name, option?.dataset.category, option?.dataset.code);
    row.classList.toggle('has-service', Boolean(name));
  };
  const syncCardQuantities = () => {
    const quantities = new Map();
    body.querySelectorAll('.order-line').forEach(row => {
      const serviceId = row.querySelector('[data-service]').value;
      if (serviceId) quantities.set(serviceId, Number(row.querySelector('[data-quantity]').value) || 0);
    });
    catalog.querySelectorAll('.service-card').forEach(card => {
      card.querySelector('[data-card-quantity]').textContent = quantities.get(card.dataset.serviceId) || 0;
    });
  };
  const reindex = () => {
    body.querySelectorAll('.order-line').forEach((row, index) => {
      row.querySelectorAll('[name]').forEach(field => {
        field.name = field.name.replace(/items\[\d+\]/, `items[${index}]`);
        field.removeAttribute('id');
      });
    });
  };
  const recalculate = () => {
    let cents = 0;
    body.querySelectorAll('.order-line').forEach(row => {
      const option = row.querySelector('[data-service]').selectedOptions[0];
      const quantity = Number(row.querySelector('[data-quantity]').value) || 0;
      const lineCents = Math.round((Number(option?.dataset.rate) || 0) * quantity * 100 + 1e-8);
      cents += lineCents;
      row.querySelector('.line-total').textContent = format(lineCents / 100);
      syncLine(row);
    });
    const discount = Number(orderForm.elements.discount.value) || 0;
    const tax = Number(orderForm.elements.tax.value) || 0;
    document.getElementById('estimated-total').textContent = format(Math.round(cents / 100 - discount + tax));
    document.getElementById('empty-ticket').hidden = body.children.length > 0 && [...body.children].some(row => row.querySelector('[data-service]').value);
    syncCardQuantities();
  };
  const selectService = card => {
    const existing = [...body.querySelectorAll('.order-line')].find(item => item.querySelector('[data-service]').value === card.dataset.serviceId);
    if (existing) {
      const quantity = existing.querySelector('[data-quantity]');
      const pieces = existing.querySelector('[data-pieces]');
      quantity.value = (Number(quantity.value) || 0) + 1;
      pieces.value = Math.min(500, (Number(pieces.value) || 0) + 1);
      recalculate();
      existing.classList.add('line-pulse');
      window.setTimeout(() => existing.classList.remove('line-pulse'), 350);
      return;
    }
    let row = [...body.querySelectorAll('.order-line')].find(item => !item.querySelector('[data-service]').value);
    if (!row) row = addLine();
    if (!row) return;
    const select = row.querySelector('[data-service]');
    select.value = card.dataset.serviceId;
    row.querySelector('[data-quantity]').value = '1';
    row.querySelector('[data-pieces]').value = '1';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    row.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  };
  const addLine = () => {
    if (body.children.length >= 50) return null;
    const row = body.firstElementChild.cloneNode(true);
    row.querySelector('[data-service]').value = '';
    row.querySelector('[data-quantity]').value = '1';
    row.querySelector('[data-pieces]').value = '1';
    body.append(row); reindex(); syncLine(row); return row;
  };
  const updateStepper = button => {
    const row = button.closest('.order-line');
    const input = row?.querySelector(`[data-${button.dataset.target}]`);
    if (!input) return;
    const min = Number(input.min) || 0;
    const max = Number(input.max) || Number.MAX_SAFE_INTEGER;
    const selectedUnit = row.querySelector('[data-service]')?.selectedOptions[0]?.dataset.unit;
    const step = button.dataset.target === 'quantity' && selectedUnit === 'PIECE'
      ? 1 : Number(input.step) || 1;
    const current = Number(input.value) || min;
    const next = button.dataset.step === 'up' ? current + step : current - step;
    input.value = String(Math.min(max, Math.max(min, Number(next.toFixed(3)))));
    input.dispatchEvent(new Event('input', { bubbles: true }));
  };
  document.querySelectorAll('.service-card').forEach(card => card.addEventListener('click', () => selectService(card)));
  document.querySelectorAll('.primary-service').forEach(button => button.addEventListener('click', () => {
    selectedCategory = button.dataset.topCategory;
    selectedGroup = 'All';
    document.querySelectorAll('.primary-service').forEach(item => item.classList.toggle('active', item === button));
    document.querySelectorAll('.dry-filter').forEach(item => item.classList.toggle('active', item.dataset.dryGroup === 'All'));
    search.value = '';
    renderCatalog();
  }));
  document.querySelectorAll('.dry-filter').forEach(button => button.addEventListener('click', () => {
    selectedGroup = button.dataset.dryGroup;
    document.querySelectorAll('.dry-filter').forEach(item => item.classList.toggle('active', item === button));
    renderCatalog();
  }));
  search.addEventListener('input', renderCatalog);
  document.getElementById('add-line').addEventListener('click', () => {
    addLine(); recalculate();
  });
  body.addEventListener('click', event => {
    const stepper = event.target.closest('[data-step]');
    if (stepper) { updateStepper(stepper); return; }
    const button = event.target.closest('.remove-line');
    if (!button) return;
    if (body.children.length > 1) {
      button.closest('.order-line').remove();
    } else {
      const row = button.closest('.order-line');
      row.querySelector('[data-service]').value = '';
      row.querySelector('[data-quantity]').value = '1';
      row.querySelector('[data-pieces]').value = '1';
    }
    reindex();
    recalculate();
  });
  customerSearch?.addEventListener('input', () => {
    const query = customerSearch.value.trim().toLowerCase();
    [...customerSelect.options].forEach(option => {
      if (!option.value) return;
      option.hidden = query.length > 0 && !(option.dataset.customerSearch || option.textContent).toLowerCase().includes(query);
    });
    if (customerSelect.selectedOptions[0]?.hidden) customerSelect.value = '';
  });
  customerSelect?.addEventListener('change', () => {
    const selected = customerSelect.selectedOptions[0];
    if (selected && customerSearch) customerSearch.value = selected.textContent.trim();
  });
  orderForm.addEventListener('input', recalculate);
  orderForm.addEventListener('change', recalculate);
  document.querySelectorAll('[data-service-image]').forEach(image => {
    const card = image.closest('.service-card');
    image.onerror = () => { image.onerror = null; image.src = '/assets/service-images/default-laundry.svg'; };
    image.src = imageFor(card.dataset.serviceName, `${card.dataset.serviceCategory} ${card.dataset.serviceGroup || ''}`, card.dataset.serviceCode);
  });
  renderCatalog();
  recalculate();
}

const servicesRows = document.getElementById('services-rows');
if (servicesRows) {
  const rows = [...servicesRows.querySelectorAll('.service-row')];
  const search = document.getElementById('services-search');
  const clear = document.getElementById('services-clear');
  const emptyClear = document.getElementById('services-empty-clear');
  const empty = document.getElementById('services-empty');
  const count = document.getElementById('services-visible-count');
  const tabs = document.getElementById('services-category-tabs');
  let category = 'All';
  const categories = ['All', ...new Set(rows.map(row => row.dataset.serviceCategory).filter(Boolean))];
  const apply = () => {
    const query = search.value.trim().toLowerCase();
    let visible = 0;
    rows.forEach(row => {
      const text = `${row.dataset.serviceName} ${row.dataset.serviceCode} ${row.dataset.serviceCategory} ${row.dataset.serviceGroup}`.toLowerCase();
      const show = (category === 'All' || row.dataset.serviceCategory === category) && (!query || text.includes(query));
      row.hidden = !show;
      if (show) visible += 1;
    });
    count.textContent = visible;
    empty.hidden = visible !== 0;
    clear.hidden = !query && category === 'All';
    document.getElementById('services-filter-note').textContent = category === 'All' ? 'Active services only' : `${category} services`;
  };
  categories.forEach(item => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'services-category-tab';
    button.dataset.category = item;
    button.setAttribute('aria-pressed', String(item === category));
    button.textContent = item;
    button.addEventListener('click', () => {
      category = item;
      tabs.querySelectorAll('button').forEach(tab => {
        const active = tab.dataset.category === category;
        tab.classList.toggle('is-active', active);
        tab.setAttribute('aria-pressed', String(active));
      });
      apply();
    });
    tabs.append(button);
  });
  tabs.firstElementChild.classList.add('is-active');
  const reset = () => { search.value = ''; category = 'All'; tabs.querySelectorAll('button').forEach(tab => { const active = tab.dataset.category === 'All'; tab.classList.toggle('is-active', active); tab.setAttribute('aria-pressed', String(active)); }); apply(); };
  search.addEventListener('input', apply);
  clear.addEventListener('click', reset);
  emptyClear.addEventListener('click', reset);
  apply();
}
