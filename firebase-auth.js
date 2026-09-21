(function () {
    const config = window.DIARY_FIREBASE_CONFIG;
    const configured = config && config.apiKey && !String(config.apiKey).startsWith('PASTE_') && config.projectId && !String(config.projectId).startsWith('PASTE_');
    let auth = null;
    let db = null;
    let currentUser = null;
    let cloudTimer = null;
    let cloudApplying = false;
    const strings = {
        uk: {
            account: 'Обліковий запис', signIn: 'Увійти', signUp: 'Зареєструватися', signOut: 'Вийти', reset: 'Скинути пароль', email: 'Email', password: 'Пароль', open: 'Вхід / реєстрація', local: 'Локально', ready: 'Firebase готовий', synced: 'Збережено у Firebase', notConfigured: 'Firebase ще не налаштовано', enterEmail: 'Введи email і пароль.', invalidEmail: 'Введи коректний email.', shortPassword: 'Пароль має містити щонайменше 6 символів.', signedIn: 'Вхід виконано.', signedOut: 'Вихід виконано.', registered: 'Акаунт створено. Перевір пошту для підтвердження email.', resetSent: 'Лист для скидання пароля надіслано.', cloudLoaded: 'Історію завантажено з Firebase.', cloudCreated: 'Firebase-сховище створено.', cloudSaved: 'Дані збережено у Firebase.', remoteChoice: 'Знайдено дані у Firebase. OK — завантажити їх, Cancel — зберегти локальні дані.', tooLarge: 'Щоденник завеликий для одного документа Firestore.', error: 'Не вдалося виконати Firebase-операцію.'
        },
        en: {
            account: 'Account', signIn: 'Sign in', signUp: 'Create account', signOut: 'Sign out', reset: 'Reset password', email: 'Email', password: 'Password', open: 'Sign in / register', local: 'Local', ready: 'Firebase ready', synced: 'Saved to Firebase', notConfigured: 'Firebase is not configured yet', enterEmail: 'Enter your email and password.', invalidEmail: 'Enter a valid email.', shortPassword: 'Password must contain at least 6 characters.', signedIn: 'Signed in.', signedOut: 'Signed out.', registered: 'Account created. Check your email to verify it.', resetSent: 'Password reset email sent.', cloudLoaded: 'History loaded from Firebase.', cloudCreated: 'Firebase storage created.', cloudSaved: 'Data saved to Firebase.', remoteChoice: 'Firebase data was found. OK loads it; Cancel keeps your local data.', tooLarge: 'This diary is too large for one Firestore document.', error: 'The Firebase operation failed.'
        },
        es: {
            account: 'Cuenta', signIn: 'Iniciar sesión', signUp: 'Crear cuenta', signOut: 'Cerrar sesión', reset: 'Restablecer contraseña', email: 'Email', password: 'Contraseña', open: 'Entrar / registrarse', local: 'Local', ready: 'Firebase listo', synced: 'Guardado en Firebase', notConfigured: 'Firebase aún no está configurado', enterEmail: 'Introduce tu email y contraseña.', invalidEmail: 'Introduce un email válido.', shortPassword: 'La contraseña debe tener al menos 6 caracteres.', signedIn: 'Sesión iniciada.', signedOut: 'Sesión cerrada.', registered: 'Cuenta creada. Revisa tu email para verificarlo.', resetSent: 'Correo de restablecimiento enviado.', cloudLoaded: 'Historial cargado desde Firebase.', cloudCreated: 'Almacenamiento de Firebase creado.', cloudSaved: 'Datos guardados en Firebase.', remoteChoice: 'Hay datos en Firebase. Aceptar los carga; Cancelar conserva los datos locales.', tooLarge: 'El diario es demasiado grande para un documento Firestore.', error: 'La operación de Firebase ha fallado.'
        },
        fr: {
            account: 'Compte', signIn: 'Se connecter', signUp: 'Créer un compte', signOut: 'Se déconnecter', reset: 'Réinitialiser le mot de passe', email: 'Email', password: 'Mot de passe', open: 'Connexion / inscription', local: 'Local', ready: 'Firebase prêt', synced: 'Enregistré dans Firebase', notConfigured: 'Firebase n’est pas encore configuré', enterEmail: 'Saisis ton email et ton mot de passe.', invalidEmail: 'Saisis une adresse email valide.', shortPassword: 'Le mot de passe doit contenir au moins 6 caractères.', signedIn: 'Connexion réussie.', signedOut: 'Déconnexion réussie.', registered: 'Compte créé. Vérifie ton email pour le confirmer.', resetSent: 'Email de réinitialisation envoyé.', cloudLoaded: 'Historique chargé depuis Firebase.', cloudCreated: 'Stockage Firebase créé.', cloudSaved: 'Données enregistrées dans Firebase.', remoteChoice: 'Des données Firebase existent. OK les charge ; Annuler conserve les données locales.', tooLarge: 'Le journal est trop volumineux pour un document Firestore.', error: 'L’opération Firebase a échoué.'
        }
    };
    const tr = key => { const lang = window.diaryApp?.getData?.().settings?.language || 'uk'; return (strings[lang] || strings.uk)[key] || strings.uk[key] || key; };
    const firebaseError = error => ({ 'auth/email-already-in-use': 'Email вже використовується.', 'auth/invalid-email': tr('invalidEmail'), 'auth/weak-password': tr('shortPassword'), 'auth/invalid-credential': 'Невірний email або пароль.', 'auth/too-many-requests': 'Забагато спроб. Спробуй пізніше.' }[error?.code] || error?.message || tr('error'));

    function createAuthUi() {
        const actions = document.querySelector('.top-actions');
        if (!actions || document.getElementById('firebase-account-button')) return;
        const button = document.createElement('button');
        button.className = 'btn';
        button.id = 'firebase-account-button';
        button.type = 'button';
        button.addEventListener('click', () => document.getElementById('firebase-auth-modal')?.classList.add('active'));
        actions.insertBefore(button, actions.querySelector('[data-action="open-sync"]'));
        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.id = 'firebase-auth-modal';
        backdrop.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-labelledby="firebase-auth-title"><div class="modal-head"><div class="modal-title" id="firebase-auth-title"></div><button class="modal-close" type="button" aria-label="Закрити" data-firebase-close>×</button></div><p class="modal-copy" id="firebase-auth-status"></p><form id="firebase-auth-form"><label class="form-label" for="firebase-auth-email"></label><input class="input" id="firebase-auth-email" type="email" autocomplete="email" required><label class="form-label" for="firebase-auth-password" style="margin-top:13px"></label><input class="input" id="firebase-auth-password" type="password" autocomplete="current-password" minlength="6" required><div class="modal-actions"><button class="btn" type="button" id="firebase-reset-button"></button><button class="btn" type="button" id="firebase-login-button"></button><button class="btn btn-primary" type="button" id="firebase-register-button"></button></div></form><div id="firebase-auth-user" hidden><div class="card"><div class="stat-label">Email</div><div id="firebase-auth-user-email" style="font-weight:800;margin-top:4px"></div></div><div class="modal-actions"><button class="btn btn-danger" type="button" id="firebase-logout-button"></button></div></div></div>`;
        document.body.appendChild(backdrop);
        backdrop.querySelector('[data-firebase-close]').addEventListener('click', () => backdrop.classList.remove('active'));
        backdrop.addEventListener('click', event => { if (event.target === backdrop) backdrop.classList.remove('active'); });
        document.getElementById('firebase-login-button').addEventListener('click', () => signIn());
        document.getElementById('firebase-register-button').addEventListener('click', () => signUp());
        document.getElementById('firebase-reset-button').addEventListener('click', () => resetPassword());
        document.getElementById('firebase-logout-button').addEventListener('click', () => auth?.signOut());
        renderAuthUi();
    }

    function renderAuthUi() {
        const button = document.getElementById('firebase-account-button');
        const title = document.getElementById('firebase-auth-title');
        const status = document.getElementById('firebase-auth-status');
        const form = document.getElementById('firebase-auth-form');
        const userPanel = document.getElementById('firebase-auth-user');
        if (!button || !title || !status) return;
        title.textContent = tr('account');
        button.textContent = currentUser ? currentUser.email : tr('open');
        button.title = currentUser ? currentUser.email : tr('open');
        document.querySelector('#firebase-auth-form .form-label[for="firebase-auth-email"]').textContent = tr('email');
        document.querySelector('#firebase-auth-form .form-label[for="firebase-auth-password"]').textContent = tr('password');
        document.getElementById('firebase-login-button').textContent = tr('signIn');
        document.getElementById('firebase-register-button').textContent = tr('signUp');
        document.getElementById('firebase-reset-button').textContent = tr('reset');
        document.getElementById('firebase-logout-button').textContent = tr('signOut');
        form.hidden = Boolean(currentUser);
        userPanel.hidden = !currentUser;
        if (currentUser) { document.getElementById('firebase-auth-user-email').textContent = currentUser.email || ''; status.textContent = tr('synced'); }
        else status.textContent = configured ? tr('ready') : tr('notConfigured');
    }

    function credentials() {
        const email = document.getElementById('firebase-auth-email')?.value.trim() || '';
        const password = document.getElementById('firebase-auth-password')?.value || '';
        if (!email || !password) { window.diaryApp.toast(tr('enterEmail'), 'error'); return null; }
        if (!/^\S+@\S+\.\S+$/.test(email)) { window.diaryApp.toast(tr('invalidEmail'), 'error'); return null; }
        if (password.length < 6) { window.diaryApp.toast(tr('shortPassword'), 'error'); return null; }
        return { email, password };
    }

    async function signIn() { const value = credentials(); if (!value || !auth) return; try { await auth.signInWithEmailAndPassword(value.email, value.password); window.diaryApp.toast(tr('signedIn'), 'success'); } catch (error) { window.diaryApp.toast(firebaseError(error), 'error'); } }
    async function signUp() { const value = credentials(); if (!value || !auth) return; try { const result = await auth.createUserWithEmailAndPassword(value.email, value.password); await result.user.sendEmailVerification().catch(() => {}); window.diaryApp.toast(tr('registered'), 'success'); } catch (error) { window.diaryApp.toast(firebaseError(error), 'error'); } }
    async function resetPassword() { const email = document.getElementById('firebase-auth-email')?.value.trim() || ''; if (!email) { window.diaryApp.toast(tr('invalidEmail'), 'error'); return; } try { await auth.sendPasswordResetEmail(email); window.diaryApp.toast(tr('resetSent'), 'success'); } catch (error) { window.diaryApp.toast(firebaseError(error), 'error'); } }

    function cloudRef() { return db.collection('users').doc(currentUser.uid).collection('diary').doc('main'); }
    async function saveCloud() {
        if (!currentUser || cloudApplying || !db) return;
        const data = window.diaryApp.sanitize(window.diaryApp.getData());
        if (JSON.stringify(data).length > 900000) { window.diaryApp.toast(tr('tooLarge'), 'error'); return; }
        try { await cloudRef().set({ schemaVersion: 2, data, preferences: window.diaryFeatures?.getSettings?.() || null, updatedAt: firebase.firestore.FieldValue.serverTimestamp() }); window.diaryApp.setSyncState(tr('synced'), 'success'); } catch (error) { window.diaryApp.setSyncState(tr('error'), 'danger'); window.diaryApp.toast(firebaseError(error), 'error'); }
    }
    function queueCloudSave() { clearTimeout(cloudTimer); cloudTimer = setTimeout(saveCloud, 900); }
    async function loadCloud() {
        if (!currentUser || !db) return;
        try {
            const snapshot = await cloudRef().get();
            if (!snapshot.exists) { await saveCloud(); window.diaryApp.setSyncState(tr('ready'), 'success'); window.diaryApp.toast(tr('cloudCreated'), 'success'); return; }
            if (window.diaryApp.hasEntries() && !window.confirm(tr('remoteChoice'))) { await saveCloud(); return; }
            cloudApplying = true;
            const remote = snapshot.data();
            window.diaryApp.setData(remote.data || {});
            if (remote.preferences) window.diaryFeatures?.setSettings?.(remote.preferences);
            window.diaryApp.save({ sync:false });
            window.diaryApp.applyTheme();
            window.diaryApp.render();
            cloudApplying = false;
            window.diaryApp.setSyncState(tr('synced'), 'success');
            window.diaryApp.toast(tr('cloudLoaded'), 'success');
        } catch (error) { cloudApplying = false; window.diaryApp.setSyncState(tr('error'), 'danger'); window.diaryApp.toast(firebaseError(error), 'error'); }
    }

    async function deleteAccount() {
        if (!currentUser || !db) { window.diaryApp.toast(tr('notConfigured'), 'error'); return; }
        try { await cloudRef().delete(); await currentUser.delete(); document.dispatchEvent(new CustomEvent('diary-account-deleted')); } catch (error) { window.diaryApp.toast(firebaseError(error), 'error'); }
    }

    function start() {
        createAuthUi();
        if (!configured || !window.firebase) return;
        try { firebase.initializeApp(config); auth = firebase.auth(); db = firebase.firestore(); auth.onAuthStateChanged(async user => { currentUser = user; renderAuthUi(); if (user) await loadCloud(); else window.diaryApp.setSyncState(tr('local'), 'warning'); }); document.addEventListener('diary-data-changed', event => { if (event.detail?.sync !== false && currentUser && !cloudApplying) queueCloudSave(); }); document.addEventListener('diary-language-changed', renderAuthUi); document.addEventListener('diary-request-account-delete', deleteAccount); } catch (error) { window.diaryApp.toast(firebaseError(error), 'error'); }
    }
    start();
})();
