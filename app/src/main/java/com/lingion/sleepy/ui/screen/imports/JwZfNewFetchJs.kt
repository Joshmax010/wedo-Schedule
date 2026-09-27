package com.lingion.sleepy.ui.screen.imports

/**
 * 新版正方在 WebView 内 fetch 课表 JSON。
 *
 * 课表数据不在页面 DOM 里，需要主动调接口取。流程：
 *   1) 路径指纹：`/jwglxt/`、`/kbcx/` 或 WebVPN 的 `/http/<hex>/`；否则报 `NOT_ON_TIMETABLE`
 *   2) `gnmkdm` 从 URL 参数取，缺省 `N2151`
 *   3) 推导接口前缀 `pathPrefix`：老版与 WebVPN 是 `/jwglxt`，新版裸 `/kbcx/` 则为空
 *   4) 三级兜底取学期 `(xnm, xqm)`：URL 参数 → 页面 `#xnm`/`#xqm` → 拉首页并让用户选
 *   5) POST `pathPrefix + /kbcx/xskbcx_cxXsgrkb.html` 取 JSON
 *   6) 嗅探响应：含 `kbList` → 成功；命中登录页特征 → 会话过期；非 JSON → 会话过期
 *   7) 通过 `window.__wedoBridge.onFetchResult` 回调 `{ok, data, format:'zf_new'}`
 *
 * 任何异常都收敛为 `{ok:false, kind:'FORMAT_ERROR'}`，不向上抛。
 */
const val ZF_NEW_FETCH_JS = """(function(){
  function fail(kind, err){
    window.__wedoBridge.onFetchResult(JSON.stringify({
      ok:false, kind:kind, err:err||'', format:'zf_new'
    }));
  }
  function ok(data, xnm, xqm, empty){
    window.__wedoBridge.onFetchResult(JSON.stringify({
      ok:true, data:data, xnm:xnm, xqm:xqm, emptySemester:!!empty, format:'zf_new'
    }));
  }
  try {
    var path = location.pathname;
    // 新版正方部署形态：旧版前缀 /jwglxt/；部分新版直接 /kbcx/；WebVPN 改写为 /http/<hex>/
    var onJwglxt = path.indexOf('/jwglxt/') >= 0;
    var onKbcx = path.indexOf('/kbcx/') >= 0;
    var onHttpHex = /\/http\/[0-9a-f]{4,8}\//.test(path);
    if (!onJwglxt && !onKbcx && !onHttpHex) {
      fail('NOT_ON_TIMETABLE', '请先导航到个人课表页(地址应含 /jwglxt/ 或 /kbcx/)');
      return;
    }
    // 推导接口前缀: WebVPN/老 jwglxt = '/jwglxt'; 新版裸 /kbcx/ = '' (path 自带 /kbcx/)
    var pathPrefix = onJwglxt || onHttpHex ? '/jwglxt' : '';

    var search = location.search.substring(1);
    var params = {};
    search.split('&').forEach(function(p){
      if (!p) return;
      var kv = p.split('=');
      params[kv[0]] = decodeURIComponent(kv[1] || '');
    });
    var gnmkdm = params['gnmkdm'] || 'N2151';

    function pickTerm(){
      return new Promise(function(resolve, reject){
        if (params['xnm'] && params['xqm']) { resolve({xnm:params['xnm'], xqm:params['xqm']}); return; }
        var sel = document.querySelector('#xnm');
        var sel2 = document.querySelector('#xqm');
        if (sel && sel.value && sel2 && sel2.value) {
          resolve({xnm:String(sel.value).trim(), xqm:String(sel2.value).trim()});
          return;
        }
        var idxPath = pathPrefix + '/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=' + encodeURIComponent(gnmkdm) + '&layout=default';
        fetch(idxPath, {credentials:'include', headers:{'X-Requested-With':'XMLHttpRequest'}})
          .then(function(r){ return r.text(); })
          .then(function(html){
            var optRe = /<option[^>]*value=["']([^"']*)["'][^>]*(\bselected\b)?[^>]*>([^<]*)<\/option>/gi;
            function parse(block){
              var arr=[], defIdx=0, m;
              while ((m = optRe.exec(block)) !== null) {
                arr.push({value:m[1], text:m[3]||m[1]});
                if (m[2]) defIdx = arr.length - 1;
              }
              return {options:arr, defaultIndex:defIdx};
            }
            var xBlock = (html.match(/<select[^>]*id=["']xnm["'][^>]*>([\s\S]*?)<\/select>/i)||[])[1]||'';
            var qBlock = (html.match(/<select[^>]*id=["']xqm["'][^>]*>([\s\S]*?)<\/select>/i)||[])[1]||'';
            var xData = parse(xBlock);
            var qData = parse(qBlock);
            function ask(data, defIdx){
              return new Promise(function(res2, rej2){
                window.__wedoBridge.onNeedTermSelection(
                  JSON.stringify(data.options),
                  defIdx,
                  function(pickedJson){
                    if (pickedJson == null || pickedJson === 'null') rej2(new Error('用户取消'));
                    else { try { res2(JSON.parse(pickedJson)); } catch(e){ rej2(e); } }
                  });
              });
            }
            return Promise.all([ask(xData, xData.defaultIndex), ask(qData, qData.defaultIndex)])
              .then(function(picks){
                if (!picks[0] || !picks[1]) throw new Error('学期选择结果无效');
                var xnm = picks[0].value || '';
                var xqm = picks[1].value || '';
                if (!xnm || !xqm) throw new Error('学期选择结果无效');
                resolve({xnm:xnm, xqm:xqm});
              });
          })
          .catch(function(e){ reject(e); });
      });
    }

    function req(url, method, body){
      var opts = {
        method: method,
        credentials: 'include',
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        }
      };
      if (body) opts.body = body;
      return fetch(url, opts).then(function(r){ return r.text(); });
    }

    pickTerm().then(function(t){
      var apiPath = pathPrefix + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + encodeURIComponent(gnmkdm);
      var body = 'xnm=' + encodeURIComponent(t.xnm) +
                 '&xqm=' + encodeURIComponent(t.xqm) +
                 '&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
      return req(apiPath, 'POST', body).then(function(text){ return {t:t, text:text}; });
    }).then(function(o){
      var text = o.text, t = o.t;
      var looksLikeLogin = text.indexOf('login_slogin') >= 0
        && text.indexOf('csrftoken') >= 0
        && (text.indexOf('请输入密码') >= 0 || text.indexOf('name="mm"') >= 0 || text.indexOf('name="password"') >= 0);
      if (looksLikeLogin) { fail('SESSION_EXPIRED', '会话已过期,请重新登录'); return; }
      var data;
      try { data = JSON.parse(text); } catch(e){ fail('SESSION_EXPIRED', '接口返回非 JSON(可能已过期): '+String(e)); return; }
      function findKbList(obj, depth){
        if (depth > 5 || !obj || typeof obj !== 'object') return null;
        if (Array.isArray(obj)) {
          for (var i=0;i<obj.length;i++){
            var r = findKbList(obj[i], depth+1);
            if (r) return r;
          }
          return null;
        }
        if (obj.kbList && Array.isArray(obj.kbList)) return obj.kbList;
        for (var k in obj) {
          var r = findKbList(obj[k], depth+1);
          if (r) return r;
        }
        return null;
      }
      var kbList = findKbList(data, 0);
      if (!kbList || kbList.length === 0) {
        ok(JSON.stringify({kbList:[]}), t.xnm, t.xqm, true);
        return;
      }
      ok(JSON.stringify({kbList:kbList}), t.xnm, t.xqm, false);
    }).catch(function(e){
      fail('FORMAT_ERROR', String(e));
    });
  } catch(err) {
    fail('FORMAT_ERROR', String(err));
  }
})();
"""
