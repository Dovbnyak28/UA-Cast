(() => {
    'use strict';

    const PREFS_KEY = 'diary_preferences_v1';
    const allowedThemes = ['dark', 'midnight', 'light'];
    const defaults = { name: '', avatar: '✦', theme: 'dark', remindersEnabled: false, reminderTime: '20:00', onboardingSeen: false };
    const messages = {
        uk: {
            profile: 'Профіль', settings: 'Налаштування', display: 'Вигляд', account: 'Акаунт', name: 'Ім’я', avatar: 'Символ', theme: 'Тема', dark: 'Темна', midnight: 'Опівніч', light: 'Світла', timezone: 'Часовий пояс', reminders: 'Нагадування', remindersHint: 'Працюють, коли браузер або PWA має дозвіл на сповіщення.', enableReminders: 'Нагадувати про щоденний запис', reminderTime: 'Час нагадування', save: 'Зберегти', cancel: 'Скасувати', exportCsv: 'Експорт CSV', printPdf: 'Друк / PDF', deleteAccount: 'Видалити акаунт', deleteAccountConfirm: 'Видалити акаунт і всі хмарні дані? Цю дію не можна скасувати.', search: 'Пошук', searchPlaceholder: 'Пошук у щоденнику…', insights: 'Твоя статистика', last14Days: 'Виконання звичок за 14 днів', waterAverage: 'Середня вода', sleepAverage: 'Середній сон', openTasks: 'Відкриті задачі', notesCount: 'Нотатки', mood: 'Настрій', noData: 'Додай кілька записів, щоб побачити тенденції.', onboardingTitle: 'Твій особистий простір', onboardingCopy: 'Записуй маленькі кроки щодня. Дані залишаються локально, а після входу синхронізуються між пристроями.', namePlaceholder: 'Як до тебе звертатися?', start: 'Почати', saved: 'Налаштування збережено.', reminderTitle: 'Час для короткого запису', reminderBody: 'Відкрий щоденник і відміть, як пройшов день.', notificationUnsupported: 'Сповіщення недоступні в цьому браузері.', needLogin: 'Спочатку увійди в Firebase-акаунт.', close: 'Закрити'
        },
        en: {
            profile: 'Profile', settings: 'Settings', display: 'Appearance', account: 'Account', name: 'Name', avatar: 'Symbol', theme: 'Theme', dark: 'Dark', midnight: 'Midnight', light: 'Light', timezone: 'Time zone', reminders: 'Reminders', remindersHint: 'They work when the browser or PWA has notification permission.', enableReminders: 'Remind me about the daily check-in', reminderTime: 'Reminder time', save: 'Save', cancel: 'Cancel', exportCsv: 'Export CSV', printPdf: 'Print / PDF', deleteAccount: 'Delete account', deleteAccountConfirm: 'Delete the account and all cloud data? This cannot be undone.', search: 'Search', searchPlaceholder: 'Search your diary…', insights: 'Your insights', last14Days: 'Habit completion over 14 days', waterAverage: 'Average water', sleepAverage: 'Average sleep', openTasks: 'Open tasks', notesCount: 'Notes', mood: 'Mood', noData: 'Add a few entries to see trends.', onboardingTitle: 'Your personal space', onboardingCopy: 'Capture small steps every day. Data stays local and syncs across devices after you sign in.', namePlaceholder: 'What should we call you?', start: 'Get started', saved: 'Settings saved.', reminderTitle: 'Time for a quick check-in', reminderBody: 'Open your diary and reflect on the day.', notificationUnsupported: 'Notifications are not available in this browser.', needLogin: 'Sign in to your Firebase account first.', close: 'Close'
        },
        es: {
            profile: 'Perfil', settings: 'Ajustes', display: 'Apariencia', account: 'Cuenta', name: 'Nombre', avatar: 'Símbolo', theme: 'Tema', dark: 'Oscuro', midnight: 'Medianoche', light: 'Claro', timezone: 'Zona horaria', reminders: 'Recordatorios', remindersHint: 'Funcionan cuando el navegador o la PWA tiene permiso para notificar.', enableReminders: 'Recordarme el registro diario', reminderTime: 'Hora del recordatorio', save: 'Guardar', cancel: 'Cancelar', exportCsv: 'Exportar CSV', printPdf: 'Imprimir / PDF', deleteAccount: 'Eliminar cuenta', deleteAccountConfirm: '¿Eliminar la cuenta y todos los datos en la nube? No se puede deshacer.', search: 'Buscar', searchPlaceholder: 'Buscar en tu diario…', insights: 'Tus estadísticas', last14Days: 'Hábitos completados en 14 días', waterAverage: 'Agua media', sleepAverage: 'Sueño medio', openTasks: 'Tareas abiertas', notesCount: 'Notas', mood: 'Ánimo', noData: 'Añade algunos registros para ver tendencias.', onboardingTitle: 'Tu espacio personal', onboardingCopy: 'Registra pequeños pasos cada día. Los datos quedan locales y se sincronizan entre dispositivos al iniciar sesión.', namePlaceholder: '¿Cómo te llamamos?', start: 'Empezar', saved: 'Ajustes guardados.', reminderTitle: 'Es hora de hacer una breve nota', reminderBody: 'Abre tu diario y revisa cómo fue el día.', notificationUnsupported: 'Las notificaciones no están disponibles en este navegador.', needLogin: 'Primero inicia sesión en tu cuenta de Firebase.', close: 'Cerrar'
        },
        fr: {
            profile: 'Profil', settings: 'Réglages', display: 'Apparence', account: 'Compte', name: 'Nom', avatar: 'Symbole', theme: 'Thème', dark: 'Sombre', midnight: 'Minuit', light: 'Clair', timezone: 'Fuseau horaire', reminders: 'Rappels', remindersHint: 'Ils fonctionnent lorsque le navigateur ou la PWA autorise les notifications.', enableReminders: 'Me rappeler la note quotidienne', reminderTime: 'Heure du rappel', save: 'Enregistrer', cancel: 'Annuler', exportCsv: 'Exporter CSV', printPdf: 'Imprimer / PDF', deleteAccount: 'Supprimer le compte', deleteAccountConfirm: 'Supprimer le compte et toutes les données cloud ? Cette action est irréversible.', search: 'Rechercher', searchPlaceholder: 'Rechercher dans le journal…', insights: 'Tes statistiques', last14Days: 'Habitudes réalisées sur 14 jours', waterAverage: 'Eau moyenne', sleepAverage: 'Sommeil moyen', openTasks: 'Tâches ouvertes', notesCount: 'Notes', mood: 'Humeur', noData: 'Ajoute quelques notes pour voir les tendances.', onboardingTitle: 'Ton espace personnel', onboardingCopy: 'Note les petits pas de chaque jour. Les données restent locales et se synchronisent entre appareils après connexion.', namePlaceholder: 'Comment doit-on t’appeler ?', start: 'Commencer', saved: 'Réglages enregistrés.', reminderTitle: 'C’est le moment de faire le point', reminderBody: 'Ouvre ton journal et regarde ta journée.', notificationUnsupported: 'Les notifications ne sont pas disponibles dans ce navigateur.', needLogin: 'Connecte-toi d’abord à ton compte Firebase.', close: 'Fermer'
        }
    };

    Object.assign(messages.uk, { accountDeleted: 'Акаунт видалено.' });
    Object.assign(messages.en, { accountDeleted: 'Account deleted.' });
    Object.assign(messages.es, { accountDeleted: 'Cuenta eliminada.' });
    Object.assign(messages.fr, { accountDeleted: 'Compte supprimé.' });

    let prefs = loadPreferences();
    let hasStoredPreferences = false;
    try { hasStoredPreferences = Boolean(localStorage.getItem(PREFS_KEY)); } catch (error) { /* private mode can reject storage */ }
    if (!hasStoredPreferences && allowedThemes.includes(appData().settings?.theme)) prefs.theme = appData().settings.theme;
    let reminderTimer = null;
    let filterTimer = null;
    let insightsFrame = 0;
    let dateFormatter = null;
    let dateFormatterZone = '';

    function appData() { return window.diaryApp?.getData?.() || { settings: {} }; }
    function language() { return appData().settings?.language || 'uk'; }
    function t(key) { return (messages[language()] || messages.uk)[key] || messages.uk[key] || key; }
    function esc(value) { return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); }
    function loadPreferences() {
        try { return { ...defaults, ...(JSON.parse(localStorage.getItem(PREFS_KEY) || '{}')) }; } catch (error) { return { ...defaults }; }
    }
    function persistPreferences(next) {
        prefs = { ...defaults, ...prefs, ...next };
        prefs.theme = allowedThemes.includes(prefs.theme) ? prefs.theme : 'dark';
        prefs.avatar = String(prefs.avatar || '✦').slice(0, 4);
        prefs.name = String(prefs.name || '').trim().slice(0, 80);
        prefs.reminderTime = /^\d{2}:\d{2}$/.test(prefs.reminderTime) ? prefs.reminderTime : '20:00';
        try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch (error) { /* private mode can reject storage */ }
        applyTheme();
        scheduleReminder();
    }
    function applyTheme() {
        const fallback = appData().settings?.theme === 'light' ? 'light' : 'dark';
        document.documentElement.dataset.theme = allowedThemes.includes(prefs.theme) ? prefs.theme : fallback;
    }
    function injectStyles() {
        if (document.getElementById('diary-features-style')) return;
        const style = document.createElement('style');
        style.id = 'diary-features-style';
        style.textContent = `
            [data-theme="midnight"] { color-scheme:dark; --bg:#03050d; --bg-raised:#080b17; --surface:rgba(9,14,29,.92); --surface-strong:#0b1020; --surface-soft:rgba(113,137,255,.08); --text:#f4f6ff; --muted:#8a98b8; --muted-2:#606d8a; --line:rgba(153,171,255,.13); --line-strong:rgba(177,190,255,.22); --accent:#7b9cff; --accent-2:#bd72ff; --accent-soft:rgba(123,156,255,.17); --shadow:0 18px 50px rgba(0,0,0,.42); --shadow-soft:0 8px 26px rgba(0,0,0,.28); }
            .feature-profile-btn { white-space:nowrap; }
            .feature-search { display:flex; align-items:center; gap:9px; margin:0 0 16px; padding:10px 12px; border:1px solid var(--line); border-radius:15px; background:var(--surface); box-shadow:var(--shadow-soft); }
            .feature-search-icon { color:var(--accent); font-size:16px; }
            .feature-search input { min-width:0; flex:1; border:0; outline:0; background:transparent; color:var(--text); }
            .feature-search input::placeholder { color:var(--muted-2); }
            .feature-insights { margin:18px 0 4px; padding:18px; border-color:rgba(157,140,255,.19); background:linear-gradient(135deg,rgba(26,31,63,.94),rgba(14,19,38,.9)); overflow:hidden; }
            [data-theme="light"] .feature-insights { background:linear-gradient(135deg,#eef0ff,#fff); }
            .feature-panel-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; margin-bottom:14px; }
            .feature-panel-title { font-size:16px; font-weight:850; letter-spacing:-.03em; }
            .feature-panel-copy { color:var(--muted); font-size:11px; margin-top:3px; }
            .feature-kpis { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; margin-bottom:15px; }
            .feature-kpi { padding:11px 12px; border:1px solid var(--line); border-radius:13px; background:var(--surface-soft); }
            .feature-kpi-label { color:var(--muted); font-size:10px; }
            .feature-kpi-value { margin-top:4px; font-size:18px; font-weight:850; letter-spacing:-.04em; }
            .feature-chart-wrap { border:1px solid var(--line); border-radius:14px; padding:7px 9px 5px; background:rgba(0,0,0,.11); }
            .feature-chart { display:block; width:100%; height:135px; }
            .feature-chart-grid { stroke:var(--line); stroke-width:1; }
            .feature-chart-line { fill:none; stroke:var(--flash-violet,var(--accent)); stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }
            .feature-chart-fill { fill:url(#feature-gradient); opacity:.24; }
            .feature-chart-labels { display:flex; justify-content:space-between; color:var(--muted-2); font-size:9px; padding:0 4px; }
            .feature-modal .modal { max-width:620px; }
            .feature-modal .modal-copy { margin-bottom:13px; }
            .feature-form-grid { display:grid; grid-template-columns:1fr 1fr; gap:11px; }
            .feature-form-grid .wide { grid-column:1/-1; }
            .feature-avatar-input { display:flex; gap:8px; align-items:center; }
            .feature-avatar-preview { display:grid; place-items:center; flex:0 0 auto; width:40px; height:40px; border-radius:13px; color:#fff; font-size:20px; background:linear-gradient(135deg,var(--accent),var(--accent-2)); }
            .feature-check { display:flex; align-items:flex-start; gap:9px; color:var(--text); font-size:12px; padding:10px 11px; border-radius:12px; background:var(--surface-soft); }
            .feature-check input { width:18px; height:18px; margin:0; accent-color:var(--accent); }
            .feature-helper { color:var(--muted); font-size:11px; line-height:1.45; }
            .feature-danger-zone { margin-top:18px; padding-top:14px; border-top:1px solid var(--line); }
            .feature-danger-zone .btn { color:var(--danger); }
            .feature-onboarding .modal { max-width:480px; text-align:center; padding:28px; }
            .feature-onboarding-mark { display:grid; place-items:center; width:64px; height:64px; margin:0 auto 14px; border-radius:21px; color:#fff; font-size:30px; background:linear-gradient(135deg,var(--accent),var(--accent-2)); box-shadow:0 14px 34px rgba(139,124,255,.25); }
            .feature-onboarding .modal-title { font-size:25px; }
            .feature-onboarding .modal-copy { max-width:360px; margin:9px auto 19px; }
            .feature-onboarding .input { margin-bottom:9px; text-align:center; }
            .feature-filter-hidden { display:none!important; }
            @media (max-width:820px) { .feature-form-grid { grid-template-columns:1fr; } .feature-form-grid .wide { grid-column:auto; } .feature-kpis { grid-template-columns:repeat(2,minmax(0,1fr)); } .feature-insights { padding:15px; } .feature-search { margin-top:2px; } }
            @media (max-width:430px) { .feature-panel-head { flex-direction:column; } .feature-panel-head .btn { width:100%; } .feature-kpi-value { font-size:16px; } }
            @media print { .sidebar,.topbar,.mobile-nav,.feature-search,.feature-profile-btn,.feature-insights .feature-actions,.toast-region { display:none!important; } .app-main { padding:0!important; } body { background:#fff!important; color:#111!important; } .card,.stat-card,.feature-insights { box-shadow:none!important; border-color:#ccc!important; background:#fff!important; color:#111!important; } .tab-content:not(.active) { display:none!important; } }
        `;
        document.head.appendChild(style);
    }
    function ensureButton() {
        const host = document.querySelector('.top-actions');
        if (!host || document.getElementById('feature-profile-button')) return;
        const button = document.createElement('button');
        button.id = 'feature-profile-button';
        button.className = 'btn feature-profile-btn';
        button.type = 'button';
        button.dataset.featureAction = 'open-profile';
        host.insertBefore(button, host.querySelector('[data-action="open-sync"]'));
    }
    function ensureSearch() {
        if (document.getElementById('feature-search')) return;
        const hero = document.querySelector('.hero');
        const firstTab = document.querySelector('.tab-content');
        if (!hero || !firstTab) return;
        const wrapper = document.createElement('div');
        wrapper.id = 'feature-search';
        wrapper.className = 'feature-search';
        wrapper.innerHTML = `<span class="feature-search-icon" aria-hidden="true">⌕</span><label class="sr-only" for="feature-search-input">${esc(t('search'))}</label><input id="feature-search-input" type="search" autocomplete="off" placeholder="${esc(t('searchPlaceholder'))}"><button class="btn btn-quiet btn-sm" type="button" data-feature-action="clear-search" aria-label="${esc(t('close'))}">×</button>`;
        hero.after(wrapper);
        wrapper.querySelector('input').addEventListener('input', filterContent);
    }
    function filterContent() {
        clearTimeout(filterTimer);
        filterTimer = setTimeout(() => {
            filterTimer = null;
            const query = String(document.getElementById('feature-search-input')?.value || '').trim().toLocaleLowerCase();
            document.querySelectorAll('.workout-card,.note-card,.todo-item,.history-row,#table-body tr').forEach(element => {
                const matches = !query || element.textContent.toLocaleLowerCase().includes(query);
                element.classList.toggle('feature-filter-hidden', !matches);
            });
        }, 40);
    }
    function ensureInsights() {
        if (document.getElementById('feature-insights')) return;
        const skills = document.getElementById('tab-skills');
        const stats = skills?.querySelector('.stats-grid');
        if (!stats) return;
        const panel = document.createElement('section');
        panel.id = 'feature-insights';
        panel.className = 'card feature-insights';
        stats.after(panel);
    }
    function recentKeys(count) {
        const keys = [];
        const now = new Date();
        const zone = appData().settings?.timeZone;
        if (!zone || zone === 'auto') {
            const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (count - 1));
            for (let index = 0; index < count; index += 1) { const date = new Date(start); date.setDate(start.getDate() + index); keys.push(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`); }
            return keys;
        }
        try {
            if (dateFormatterZone !== zone) { dateFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }); dateFormatterZone = zone; }
            const parts = Object.fromEntries(dateFormatter.formatToParts(now).filter(part => part.type !== 'literal').map(part => [part.type, Number(part.value)]));
            const start = new Date(Date.UTC(parts.year, parts.month - 1, parts.day - (count - 1)));
            for (let index = 0; index < count; index += 1) { const date = new Date(start); date.setUTCDate(start.getUTCDate() + index); keys.push(`${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`); }
            return keys;
        } catch (error) {
            return recentKeysForLocalDate(count, now);
        }
    }
    function recentKeysForLocalDate(count, now) {
        const keys = [];
        const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (count - 1));
        for (let index = 0; index < count; index += 1) { const date = new Date(start); date.setDate(start.getDate() + index); keys.push(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`); }
        return keys;
    }
    function renderInsights() {
        if (insightsFrame) return;
        insightsFrame = requestAnimationFrame(() => { insightsFrame = 0; renderInsightsNow(); });
    }
    function renderInsightsNow() {
        const panel = document.getElementById('feature-insights');
        if (!panel) return;
        const data = appData();
        const keys = recentKeys(14);
        const points = keys.map(key => { let done = 0; data.habits.forEach(habit => { if (habit.history?.[key] === 'success' || habit.history?.[key] === true) done += 1; }); return data.habits.length ? Math.round(done / data.habits.length * 100) : 0; });
        const waterValues = recentKeys(7).map(key => Number(data.health?.waterHistory?.[key])).filter(Number.isFinite);
        const sleepValues = recentKeys(7).map(key => Number(data.health?.sleepHistory?.[key])).filter(Number.isFinite);
        const waterAvg = waterValues.length ? `${Math.round(waterValues.reduce((a, b) => a + b, 0) / waterValues.length)} ml` : '—';
        const sleepAvg = sleepValues.length ? `${(sleepValues.reduce((a, b) => a + b, 0) / sleepValues.length).toFixed(1)} h` : '—';
        const moodValues = recentKeys(14).map(key => data.health?.moodHistory?.[key]).filter(Boolean);
        const mood = moodValues.length ? moodValues[moodValues.length - 1] : '—';
        const openTasks = data.todos.filter(todo => !todo.completed).length;
        const notes = data.notes.length;
        const width = 420, height = 120, max = 100;
        const coords = points.map((value, index) => `${Math.round(index * width / Math.max(points.length - 1, 1))},${Math.round(height - value / max * 92 - 8)}`).join(' ');
        const fill = `0,${height} ${coords} ${width},${height}`;
        const labels = [keys[0].slice(5), keys[6].slice(5), keys[13].slice(5)];
        panel.innerHTML = `<div class="feature-panel-head"><div><div class="feature-panel-title">${esc(t('insights'))}</div><div class="feature-panel-copy">${esc(t('last14Days'))}</div></div><div class="feature-actions"><button class="btn btn-sm" type="button" data-feature-action="export-csv">↓ ${esc(t('exportCsv'))}</button><button class="btn btn-sm" type="button" data-feature-action="print-pdf">↗ ${esc(t('printPdf'))}</button></div></div><div class="feature-kpis"><div class="feature-kpi"><div class="feature-kpi-label">${esc(t('waterAverage'))}</div><div class="feature-kpi-value">${esc(waterAvg)}</div></div><div class="feature-kpi"><div class="feature-kpi-label">${esc(t('sleepAverage'))}</div><div class="feature-kpi-value">${esc(sleepAvg)}</div></div><div class="feature-kpi"><div class="feature-kpi-label">${esc(t('openTasks'))}</div><div class="feature-kpi-value">${openTasks}</div></div><div class="feature-kpi"><div class="feature-kpi-label">${esc(t('mood'))}</div><div class="feature-kpi-value">${esc(mood)}</div></div></div>${data.habits.length || waterValues.length || sleepValues.length ? `<div class="feature-chart-wrap"><svg class="feature-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(t('last14Days'))}"><defs><linearGradient id="feature-gradient" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="var(--accent)"/><stop offset="1" stop-color="var(--accent-2)" stop-opacity="0"/></linearGradient></defs><line class="feature-chart-grid" x1="0" y1="28" x2="420" y2="28"/><line class="feature-chart-grid" x1="0" y1="74" x2="420" y2="74"/><polygon class="feature-chart-fill" points="${fill}"/><polyline class="feature-chart-line" points="${coords}"/></svg><div class="feature-chart-labels"><span>${esc(labels[0])}</span><span>${esc(labels[1])}</span><span>${esc(labels[2])}</span></div></div>` : `<div class="empty">${esc(t('noData'))}</div>`}<div class="feature-panel-copy" style="margin-top:10px">${notes} ${esc(t('notesCount').toLocaleLowerCase())}</div>`;
    }
    function timezoneOptions(selected) {
        const zones = ['auto', 'UTC', 'Europe/Paris', 'Europe/Kyiv', 'America/New_York', 'America/Los_Angeles', 'Asia/Tokyo'];
        const labels = { auto: language() === 'uk' ? 'Автоматично (пристрій)' : language() === 'en' ? 'Automatic (device)' : language() === 'es' ? 'Automático (dispositivo)' : 'Automatique (appareil)' };
        return zones.map(zone => `<option value="${zone}" ${zone === selected ? 'selected' : ''}>${esc(labels[zone] || zone)}</option>`).join('');
    }
    function ensureModals() {
        if (!document.getElementById('feature-profile-modal')) {
            const profile = document.createElement('div');
            profile.id = 'feature-profile-modal'; profile.className = 'modal-backdrop feature-modal'; profile.dataset.featureOverlay = 'profile'; document.body.appendChild(profile);
        }
        if (!document.getElementById('feature-onboarding-modal')) {
            const onboarding = document.createElement('div');
            onboarding.id = 'feature-onboarding-modal'; onboarding.className = 'modal-backdrop feature-modal feature-onboarding'; onboarding.dataset.featureOverlay = 'onboarding'; document.body.appendChild(onboarding);
        }
    }
    function renderProfile() {
        const modal = document.getElementById('feature-profile-modal');
        if (!modal) return;
        const settings = appData().settings || {};
        const profile = modal.querySelector('.modal');
        modal.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-labelledby="feature-profile-title"><div class="modal-head"><div class="modal-title" id="feature-profile-title">${esc(t('profile'))}</div><button class="modal-close" type="button" data-feature-action="close-profile" aria-label="${esc(t('close'))}">×</button></div><p class="modal-copy">${esc(t('settings'))} · ${esc(t('account'))}</p><form id="feature-profile-form"><div class="feature-form-grid"><div class="wide"><label class="form-label" for="feature-profile-name">${esc(t('name'))}</label><input class="input" id="feature-profile-name" name="name" maxlength="80" placeholder="${esc(t('namePlaceholder'))}" value="${esc(prefs.name)}" autocomplete="name"></div><div><label class="form-label" for="feature-profile-avatar">${esc(t('avatar'))}</label><div class="feature-avatar-input"><span class="feature-avatar-preview" id="feature-avatar-preview">${esc(prefs.avatar)}</span><input class="input" id="feature-profile-avatar" name="avatar" maxlength="4" value="${esc(prefs.avatar)}" inputmode="text"></div></div><div><label class="form-label" for="feature-profile-timezone">${esc(t('timezone'))}</label><select class="select" id="feature-profile-timezone" name="timeZone">${timezoneOptions(settings.timeZone || 'auto')}</select></div><div><label class="form-label" for="feature-profile-theme">${esc(t('theme'))}</label><select class="select" id="feature-profile-theme" name="theme"><option value="dark" ${prefs.theme === 'dark' ? 'selected' : ''}>${esc(t('dark'))}</option><option value="midnight" ${prefs.theme === 'midnight' ? 'selected' : ''}>${esc(t('midnight'))}</option><option value="light" ${prefs.theme === 'light' ? 'selected' : ''}>${esc(t('light'))}</option></select></div><div><label class="form-label" for="feature-reminder-time">${esc(t('reminderTime'))}</label><input class="input" id="feature-reminder-time" name="reminderTime" type="time" value="${esc(prefs.reminderTime)}"></div><div class="wide"><label class="feature-check"><input id="feature-reminders-enabled" name="remindersEnabled" type="checkbox" ${prefs.remindersEnabled ? 'checked' : ''}><span><strong>${esc(t('enableReminders'))}</strong><br><span class="feature-helper">${esc(t('remindersHint'))}</span></span></label></div></div><div class="modal-actions"><button class="btn" type="button" data-feature-action="close-profile">${esc(t('cancel'))}</button><button class="btn btn-primary" type="submit">${esc(t('save'))}</button></div></form><div class="modal-actions" style="justify-content:flex-start"><button class="btn" type="button" data-feature-action="export-csv">↓ ${esc(t('exportCsv'))}</button><button class="btn" type="button" data-feature-action="print-pdf">↗ ${esc(t('printPdf'))}</button></div><div class="feature-danger-zone"><button class="btn btn-quiet" type="button" data-feature-action="delete-account">${esc(t('deleteAccount'))}</button></div></div>`;
        modal.querySelector('#feature-profile-form').addEventListener('submit', saveProfile);
        modal.querySelector('#feature-profile-avatar').addEventListener('input', event => { modal.querySelector('#feature-avatar-preview').textContent = event.target.value.slice(0, 4) || '✦'; });
    }
    async function saveProfile(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const settings = appData().settings || {};
        const selectedTimeZone = form.timeZone.value || 'auto';
        settings.timeZone = selectedTimeZone;
        persistPreferences({ name: form.name.value, avatar: form.avatar.value, theme: form.theme.value, remindersEnabled: form.remindersEnabled.checked, reminderTime: form.reminderTime.value });
        if (window.diaryApp?.save) window.diaryApp.save({ sync: true });
        document.dispatchEvent(new CustomEvent('diary-profile-changed'));
        if (prefs.remindersEnabled && 'Notification' in window && Notification.permission === 'default') { try { await Notification.requestPermission(); } catch (error) { /* permission can be denied */ } }
        document.getElementById('feature-profile-modal')?.classList.remove('active');
        renderAllFeatures();
        window.diaryApp?.toast?.(t('saved'), 'success');
    }
    function renderOnboarding() {
        const modal = document.getElementById('feature-onboarding-modal');
        if (!modal) return;
        modal.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-labelledby="feature-onboarding-title"><div class="feature-onboarding-mark">${esc(prefs.avatar || '✦')}</div><div class="modal-title" id="feature-onboarding-title">${esc(t('onboardingTitle'))}</div><p class="modal-copy">${esc(t('onboardingCopy'))}</p><form id="feature-onboarding-form"><label class="sr-only" for="feature-onboarding-name">${esc(t('name'))}</label><input class="input" id="feature-onboarding-name" maxlength="80" placeholder="${esc(t('namePlaceholder'))}" autocomplete="name" value="${esc(prefs.name)}"><button class="btn btn-primary" type="submit">${esc(t('start'))}</button></form></div>`;
        modal.querySelector('#feature-onboarding-form').addEventListener('submit', event => { event.preventDefault(); persistPreferences({ name: modal.querySelector('#feature-onboarding-name').value, onboardingSeen: true }); modal.classList.remove('active'); renderAllFeatures(); });
    }
    function openProfile() { ensureModals(); renderProfile(); document.getElementById('feature-profile-modal').classList.add('active'); document.getElementById('feature-profile-name')?.focus(); }
    function exportCsv() {
        const data = appData();
        const rows = [['type', 'date', 'name', 'value', 'details']];
        data.habits.forEach(habit => Object.entries(habit.history || {}).forEach(([date, value]) => rows.push(['habit', date, habit.name, value, ''])));
        data.workouts.forEach(item => rows.push(['workout', item.date, item.name, item.category, item.details]));
        data.todos.forEach(item => rows.push(['todo', '', item.text, item.completed ? 'completed' : 'open', '']));
        data.notes.forEach(item => rows.push(['note', item.date, item.text, '', '']));
        Object.entries(data.health?.waterHistory || {}).forEach(([date, value]) => rows.push(['water', date, '', value, 'ml']));
        Object.entries(data.health?.moodHistory || {}).forEach(([date, value]) => rows.push(['mood', date, '', value, '']));
        Object.entries(data.health?.sleepHistory || {}).forEach(([date, value]) => rows.push(['sleep', date, '', value, 'hours']));
        const csv = rows.map(row => row.map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
        const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `diary-${new Date().toISOString().slice(0, 10)}.csv`; link.click(); URL.revokeObjectURL(url);
        window.diaryApp?.toast?.(t('exportCsv'), 'success');
    }
    function printPdf() { window.print(); }
    function deleteAccount() {
        if (!window.confirm(t('deleteAccountConfirm'))) return;
        document.dispatchEvent(new CustomEvent('diary-request-account-delete'));
    }
    function notifyReminder() {
        const title = t('reminderTitle'); const body = t('reminderBody');
        if ('Notification' in window && Notification.permission === 'granted') { if (navigator.serviceWorker?.controller) navigator.serviceWorker.ready.then(registration => registration.showNotification(title, { body, tag: 'diary-reminder' })).catch(() => new Notification(title, { body })); else new Notification(title, { body }); }
        else window.diaryApp?.toast?.(`${title} — ${body}`);
    }
    function scheduleReminder() {
        clearTimeout(reminderTimer); reminderTimer = null;
        if (!prefs.remindersEnabled) return;
        const [hours, minutes] = prefs.reminderTime.split(':').map(Number); const target = new Date(); target.setHours(hours, minutes, 0, 0); if (target <= new Date()) target.setDate(target.getDate() + 1);
        reminderTimer = setTimeout(() => { notifyReminder(); scheduleReminder(); }, Math.max(1000, target.getTime() - Date.now()));
    }
    function renderAllFeatures() {
        applyTheme(); ensureButton(); ensureSearch(); ensureInsights(); ensureModals();
        const button = document.getElementById('feature-profile-button'); if (button) { button.textContent = `${prefs.avatar || '✦'} ${t('profile')}`; button.title = t('profile'); }
        const search = document.getElementById('feature-search-input'); if (search) search.placeholder = t('searchPlaceholder');
        renderInsights(); filterContent();
        if (document.getElementById('feature-profile-modal')?.classList.contains('active')) renderProfile();
        if (document.getElementById('feature-onboarding-modal')?.classList.contains('active')) renderOnboarding();
    }
    document.addEventListener('click', event => {
        const action = event.target.closest('[data-feature-action]')?.dataset.featureAction;
        if (!action) return;
        if (action === 'open-profile') openProfile();
        else if (action === 'close-profile') document.getElementById('feature-profile-modal')?.classList.remove('active');
        else if (action === 'clear-search') { const input = document.getElementById('feature-search-input'); if (input) { input.value = ''; filterContent(); input.focus(); } }
        else if (action === 'export-csv') exportCsv();
        else if (action === 'print-pdf') printPdf();
        else if (action === 'delete-account') deleteAccount();
    });
    document.addEventListener('click', event => { if (event.target.closest('[data-action="toggle-theme"]')) setTimeout(() => persistPreferences({ theme: document.documentElement.dataset.theme }), 0); });
    document.addEventListener('click', event => { if (event.target.matches('.feature-modal')) event.target.classList.remove('active'); });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') document.querySelectorAll('.feature-modal.active').forEach(modal => modal.classList.remove('active')); });
    document.addEventListener('diary-data-changed', () => { renderInsights(); filterContent(); });
    document.addEventListener('diary-language-changed', renderAllFeatures);
    document.addEventListener('diary-account-deleted', () => { persistPreferences({ ...defaults, onboardingSeen: true }); document.getElementById('feature-profile-modal')?.classList.remove('active'); window.diaryApp?.toast?.(t('accountDeleted'), 'success'); });
    window.diaryFeatures = {
        getSettings: () => ({ ...prefs }),
        setSettings: remote => { if (remote && typeof remote === 'object') persistPreferences(remote); renderAllFeatures(); },
        openProfile,
        render: renderAllFeatures
    };
    injectStyles(); renderAllFeatures(); scheduleReminder();
    if (!prefs.onboardingSeen) setTimeout(() => { if (!document.querySelector('.modal-backdrop.active')) { renderOnboarding(); document.getElementById('feature-onboarding-modal')?.classList.add('active'); } }, 700);
})();
