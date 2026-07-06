(function (window, document) {
    'use strict';

    if (!window || !document) {
        return;
    }

    function getCurrentScript() {
        if (document.currentScript) {
            return document.currentScript;
        }
        var scripts = document.getElementsByTagName('script');
        for (var i = scripts.length - 1; i >= 0; i -= 1) {
            var src = scripts[i].getAttribute('src') || '';
            if (src.indexOf('/embed.js') !== -1 || src.indexOf('/embed.min.js') !== -1) {
                return scripts[i];
            }
        }
        return null;
    }

    function resolveBaseUrl(script) {
        try {
            return new URL(script.src, window.location.href).origin;
        } catch (error) {
            return window.location.origin;
        }
    }

    function buildLauncherIcon() {
        return (
            '<svg t="1744708389439" class="icon" viewBox="0 0 1029 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
            '<path fill="rgb(59 130 246)" d="M513.128884 0.000513a512 512 0 1 0 511.281619 513.128884v-0.718381A511.28162 511.28162 0 0 0 513.744638 0.000513z"></path>' +
            '<path fill="#ffffff" d="M420 300h90v350h200v80H420z"></path>' +
            '</svg>'
        );
    }

    var scriptTag = getCurrentScript();
    if (!scriptTag) {
        return;
    }

    window.chatbotConfig = window.chatbotConfig || {};
    var config = window.chatbotConfig;

    var appCode = String(
        config.AppCode ||
            config.appCode ||
            scriptTag.getAttribute('data-app-code') ||
            scriptTag.id ||
            ''
    ).trim();
    if (!appCode) {
        if (window.console && typeof window.console.warn === 'function') {
            window.console.warn('[embed.js] Missing AppCode. Set window.chatbotConfig.AppCode.');
        }
        return;
    }

    var buttonId = 'skill-chatbot-bubble-button';
    var panelId = 'skill-chatbot-bubble-window';
    if (document.getElementById(buttonId) || document.getElementById(panelId)) {
        return;
    }

    var baseUrl = resolveBaseUrl(scriptTag);
    var iframeUrl = baseUrl + '/chatbot/' + encodeURIComponent(appCode);
    var zIndex = '2147480000';

    var style = document.createElement('style');
    style.type = 'text/css';
    style.textContent =
        '#' +
        buttonId +
        '{position:fixed;bottom:24px;right:24px;width:56px;height:56px;border:0;border-radius:9999px;background:transparent;display:flex;align-items:center;justify-content:center;cursor:pointer;z-index:' +
        zIndex +
        ';padding:0;box-shadow:0 12px 30px rgba(15,23,42,.25);transition:transform .2s ease,opacity .2s ease;}' +
        '#' +
        buttonId +
        ':hover{transform:translateY(-2px);}' +
        '#' +
        buttonId +
        ' svg{width:56px;height:56px;display:block;}' +
        '#' +
        panelId +
        '{position:fixed;bottom:88px;right:24px;width:min(calc(100vw - 24px), 24rem);height:min(calc(100vh - 124px), 40rem);border-radius:16px;background:#fff;box-shadow:0 16px 48px rgba(15,23,42,.24);border:1px solid rgba(226,232,240,.9);overflow:hidden;z-index:' +
        zIndex +
        ';display:none;}' +
        '#' +
        panelId +
        '.is-open{display:block;}' +
        '#' +
        panelId +
        ' .skill-chatbot-frame{width:100%;height:100%;border:0;display:block;background:#fff;}';
    document.head.appendChild(style);

    var panel = document.createElement('div');
    panel.id = panelId;
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', 'Chatbot');

    var iframe = document.createElement('iframe');
    iframe.className = 'skill-chatbot-frame';
    iframe.src = iframeUrl;
    iframe.allow = 'clipboard-write; microphone';
    iframe.title = 'Chatbot';

    panel.appendChild(iframe);
    document.body.appendChild(panel);

    var button = document.createElement('button');
    button.id = buttonId;
    button.type = 'button';
    button.innerHTML = buildLauncherIcon();
    button.setAttribute('aria-label', 'Open chat');
    button.setAttribute('aria-controls', panelId);
    button.setAttribute('aria-expanded', 'false');
    document.body.appendChild(button);

    function togglePanel(nextOpen) {
        var shouldOpen =
            typeof nextOpen === 'boolean' ? nextOpen : !panel.classList.contains('is-open');
        panel.classList.toggle('is-open', shouldOpen);
        button.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
    }

    button.addEventListener('click', function () {
        togglePanel();
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            togglePanel(false);
        }
    });
})(window, document);
