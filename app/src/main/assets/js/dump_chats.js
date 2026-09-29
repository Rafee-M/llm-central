// dump_chats.js — injected into the chat page by the app (loaded via web/Scripts.java).
//
// Offline history for duck.ai: duck.ai stores chats in the browser storage on the phone
// (IndexedDB + localStorage). This script reads all of it, converts images/files to text (base64 data
// URLs) and sends everything to the app (Android.onChatsFetched), which saves it for offline viewing.
//
// Moved unchanged from a Java string in MainActivity (Phase 0.4); only line breaks were added.

(function() {
  function convertBlobs(obj, depth, seen) {
    depth = depth || 0;
    seen = seen || new Set();
    return new Promise(function(resolve) {
      if (!obj || depth > 20) return resolve(obj);
      if (typeof Date !== 'undefined' && obj instanceof Date) {
        return resolve(obj.toISOString());
      }
      if (typeof Blob !== 'undefined' && (obj instanceof Blob || obj instanceof File)) {
        try {
          let reader = new FileReader();
          reader.onloadend = function() { resolve(reader.result); };
          reader.onerror = function() { resolve(null); };
          reader.readAsDataURL(obj);
        } catch(e) { resolve(null); }
        return;
      }
      if (typeof ArrayBuffer !== 'undefined' && (obj instanceof ArrayBuffer || ArrayBuffer.isView(obj))) {
        try {
          let bytes = new Uint8Array(obj.buffer || obj);
          let blob = new Blob([bytes]);
          let reader = new FileReader();
          reader.onloadend = function() { resolve(reader.result); };
          reader.onerror = function() { resolve(null); };
          reader.readAsDataURL(blob);
        } catch(e) { resolve(null); }
        return;
      }
      if (Array.isArray(obj)) {
        if (seen.has(obj)) return resolve('[]');
        seen.add(obj);
        var promises = obj.map(function(item) { return convertBlobs(item, depth + 1, seen); });
        Promise.all(promises).then(resolve).catch(function() { resolve(obj); });
        return;
      }
      if (typeof obj === 'object') {
        if (seen.has(obj)) return resolve('{}');
        seen.add(obj);
        var keys = Object.keys(obj);
        var promises = keys.map(function(k) { return convertBlobs(obj[k], depth + 1, seen); });
        Promise.all(promises).then(function(values) {
          var newObj = {};
          keys.forEach(function(k, idx) { newObj[k] = values[idx]; });
          resolve(newObj);
        }).catch(function() { resolve(obj); });
        return;
      }
      resolve(obj);
    });
  }
  function dump() {
    let knownDbs = ['savedAIChatData', 'duck-ai-chats', 'saved-chats', 'aiChatData'];
    let getDbs = (window.indexedDB && window.indexedDB.databases) ? window.indexedDB.databases() : Promise.resolve([]);
    getDbs.then(async (dbs) => {
      let dbNamesSet = new Set((dbs || []).map(d => d.name).filter(Boolean));
      if (dbNamesSet.size === 0) {
        knownDbs.forEach(k => dbNamesSet.add(k));
      }
      let dbNames = Array.from(dbNamesSet);
      let result = {};
      for (let dbName of dbNames) {
        let res = await new Promise((resolve) => {
          try {
            let req = window.indexedDB.open(dbName);
            req.onerror = () => resolve(null);
            req.onsuccess = (e) => {
              let db = e.target.result;
              db.onversionchange = () => { try { db.close(); } catch(err){} };
              let storeNames = Array.from(db.objectStoreNames);
              if (storeNames.length === 0) {
                try { db.close(); } catch(err){}
                resolve(null);
                return;
              }
              let dbData = {};
              let completed = 0;
              let hasTimedOut = false;
              let timeoutTimer = setTimeout(() => {
                hasTimedOut = true;
                try { db.close(); } catch(err){}
                resolve(dbData);
              }, 15000);
              storeNames.forEach((storeName) => {
                try {
                  let tx = db.transaction(storeName, 'readonly');
                  let store = tx.objectStore(storeName);
                  let getAllReq = store.getAll();
                  getAllReq.onsuccess = async () => {
                    if (hasTimedOut) return;
                    try {
                      dbData[storeName] = await convertBlobs(getAllReq.result);
                    } catch(err) {
                      dbData[storeName] = getAllReq.result;
                    }
                    completed++;
                    if (completed === storeNames.length) {
                      clearTimeout(timeoutTimer);
                      try { db.close(); } catch(err){}
                      resolve(dbData);
                    }
                  };
                  getAllReq.onerror = () => {
                    if (hasTimedOut) return;
                    completed++;
                    if (completed === storeNames.length) {
                      clearTimeout(timeoutTimer);
                      try { db.close(); } catch(err){}
                      resolve(dbData);
                    }
                  };
                } catch(err) {
                  if (hasTimedOut) return;
                  completed++;
                  if (completed === storeNames.length) {
                    clearTimeout(timeoutTimer);
                    try { db.close(); } catch(e){}
                    resolve(dbData);
                  }
                }
              });
            };
          } catch(err) { resolve(null); }
        });
        if (res && Object.keys(res).length > 0) result[dbName] = res;
      }
      if (window.__duckImgStore && window.__duckImgStore.size > 0) {
        result['savedAIChatData'] = result['savedAIChatData'] || {};
        result['savedAIChatData']['chat-images'] = result['savedAIChatData']['chat-images'] || [];
        for (let [k, item] of window.__duckImgStore.entries()) {
          try {
            let converted = await convertBlobs(item);
            if (converted) result['savedAIChatData']['chat-images'].push(converted);
          } catch(e) {}
        }
      }
      let localData = {};
      try {
        for (let i = 0; i < localStorage.length; i++) {
          let key = localStorage.key(i);
          localData[key] = localStorage.getItem(key);
        }
      } catch(e){}
      let blobData = {};
      if (window.blobMap && window.blobMap instanceof Map) {
        for (let [bUrl, bObj] of window.blobMap.entries()) {
          try {
            blobData[bUrl] = await convertBlobs(bObj);
          } catch(e) {}
        }
      }
      let domImages = [];
      try {
        let imgs = document.querySelectorAll('img');
        for (let i = 0; i < imgs.length; i++) {
          let img = imgs[i];
          let src = img.src || '';
          if (src) {
            try {
              if (img.naturalWidth > 30 && img.naturalHeight > 30) {
                let canvas = document.createElement('canvas');
                canvas.width = img.naturalWidth;
                canvas.height = img.naturalHeight;
                let ctx = canvas.getContext('2d');
                ctx.drawImage(img, 0, 0);
                let b64 = canvas.toDataURL('image/jpeg', 0.9);
                domImages.push({ src: src, alt: img.alt || '', dataUrl: b64 });
              } else if (src.startsWith('data:')) {
                domImages.push({ src: src, alt: img.alt || '', dataUrl: src });
              }
            } catch(e) {}
          }
        }
      } catch(e) {}
      if (typeof Android !== 'undefined' && Android.onChatsFetched) {
        Android.onChatsFetched(JSON.stringify({
          indexedDB: result,
          localStorage: localData,
          blobMap: blobData,
          domImages: domImages
        }));
      }
    }).catch(err => {
      if (typeof Android !== 'undefined' && Android.onChatsFetched) {
        Android.onChatsFetched(JSON.stringify({error: err.toString()}));
      }
    });
  }
  setTimeout(dump, 150);
})();
