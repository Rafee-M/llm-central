// blob_download_patch.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Download helper + image-storage fallback. Injected into duck.ai on every page load.
// 1) Remembers every "blob:" object the page creates (URL.createObjectURL), so when the user downloads a
// blob: link, the app can read the file's bytes (see MainActivity's DownloadListener).
// 2) If duck.ai asks IndexedDB for a "chat-images" store that doesn't exist, returns a fake in-memory
// store instead of failing, so image chats keep working.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
    if (!window.blobHandlerInjected) {
        window.blobHandlerInjected = true;
        window.blobMap = window.blobMap || new Map();
        const oC = URL.createObjectURL;
        URL.createObjectURL = function(b) {
            const u = oC.call(URL, b);
            if (b instanceof Blob) window.blobMap.set(u, b);
            console.log('Blob created: ' + u);
            return u;
        };
        console.log('Blob Handler Patch Active');
    }
    if (!window.idbImageFallbackInjected && typeof IDBDatabase !== 'undefined') {
        window.idbImageFallbackInjected = true;
        const origTx = IDBDatabase.prototype.transaction;
        window.__duckImgStore = window.__duckImgStore || new Map();
        function createFakeReq(res) {
            var listeners = {};
            var req = {
                result: res,
                error: null,
                readyState: 'done',
                onsuccess: null,
                onerror: null,
                addEventListener: function(type, cb) {
                    if (!listeners[type]) listeners[type] = [];
                    listeners[type].push(cb);
                },
                removeEventListener: function(type, cb) {
                    if (listeners[type]) listeners[type] = listeners[type].filter(function(fn) { return fn !== cb; });
                }
            };
            setTimeout(function() {
                var evt = { type: 'success', target: req, currentTarget: req };
                if (typeof req.onsuccess === 'function') { try { req.onsuccess.call(req, evt); } catch(e){} }
                if (listeners['success']) { listeners['success'].forEach(function(fn) { try { fn.call(req, evt); } catch(e){} }); }
            }, 0);
            return req;
        }
        IDBDatabase.prototype.transaction = function(storeNames, mode) {
            var names = Array.isArray(storeNames) ? storeNames : [storeNames];
            if (names.indexOf('chat-images') !== -1 && !this.objectStoreNames.contains('chat-images')) {
                console.warn('[DuckAssist] Providing fallback for missing chat-images store');
                var storeMap = window.__duckImgStore;
                var txListeners = {};
                var fakeTx = {
                    oncomplete: null,
                    onerror: null,
                    onabort: null,
                    abort: function() {},
                    addEventListener: function(type, cb) {
                        if (!txListeners[type]) txListeners[type] = [];
                        txListeners[type].push(cb);
                    },
                    removeEventListener: function(type, cb) {
                        if (txListeners[type]) txListeners[type] = txListeners[type].filter(function(fn) { return fn !== cb; });
                    },
                    objectStore: function(name) {
                        return {
                            add: function(val) {
                                var key = (val && val.uuid) ? val.uuid : ('img_' + Date.now());
                                if (val) storeMap.set(key, val);
                                return createFakeReq(key);
                            },
                            put: function(val) {
                                var key = (val && val.uuid) ? val.uuid : ('img_' + Date.now());
                                if (val) storeMap.set(key, val);
                                return createFakeReq(key);
                            },
                            get: function(key) {
                                return createFakeReq(storeMap.get(key));
                            },
                            getAll: function() {
                                return createFakeReq(Array.from(storeMap.values()));
                            },
                            delete: function(key) {
                                storeMap.delete(key);
                                return createFakeReq(undefined);
                            },
                            clear: function() {
                                storeMap.clear();
                                return createFakeReq(undefined);
                            },
                            index: function(indexName) {
                                return {
                                    getAll: function(chatId) {
                                        var arr = Array.from(storeMap.values()).filter(function(v) { return v && v.chatId === chatId; });
                                        return createFakeReq(arr);
                                    }
                                };
                            }
                        };
                    }
                };
                setTimeout(function() {
                    var evt = { type: 'complete', target: fakeTx, currentTarget: fakeTx };
                    if (typeof fakeTx.oncomplete === 'function') { try { fakeTx.oncomplete.call(fakeTx, evt); } catch(e){} }
                    if (txListeners['complete']) { txListeners['complete'].forEach(function(fn) { try { fn.call(fakeTx, evt); } catch(e){} }); }
                }, 5);
                return fakeTx;
            }
            return origTx.apply(this, arguments);
        };
    }
})();
