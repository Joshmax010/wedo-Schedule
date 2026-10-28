// SPDX-License-Identifier: GPL-3.0-only
// Derived from wedo JwZfNewFetchJs; same-origin requests, no native password/JS bridge.
package com.wedo.jwimport

import org.json.JSONObject

internal object FetchScripts {
    fun terms(school: SchoolDefinition): String = fetch(
        school.basePath + "/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=" + school.functionCode + "&layout=default", null, false)

    fun schedule(school: SchoolDefinition, term: TermSelection): String = fetch(
        school.basePath + "/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=" + school.functionCode,
        "xnm=${term.year}&xqm=${term.semester}&kzlx=ck&xsdm=&kclbdm=&kclxdm=", true)

    private fun fetch(path: String, body: String?, schedule: Boolean): String = """
        async function read(abort) {
          const response = await fetch(${JSONObject.quote(path)}, {
            method: '${if (body == null) "GET" else "POST"}', credentials: 'same-origin', redirect: 'error',
            signal: abort.signal,
            headers: {'X-Requested-With':'XMLHttpRequest', 'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
            ${if (body == null) "" else "body: " + JSONObject.quote(body) + ","}
          });
          if (response.status === 401 || response.status === 403) throw 'SESSION_EXPIRED';
          if (!response.ok) throw 'SERVER';
          const text = await response.text();
          if (text.length > ${ZhengfangParser.MAX_RESPONSE_CHARS}) throw 'INVALID_RESPONSE';
          if (${schedule}) {
            let data;
            try { data = JSON.parse(text); } catch (_) {
              if (new DOMParser().parseFromString(text, 'text/html').querySelector('input[type="password"]')) throw 'SESSION_EXPIRED';
              throw 'INVALID_RESPONSE';
            }
            if (!data || !Array.isArray(data.kbList)) throw 'INVALID_RESPONSE';
            return JSON.stringify({kbList: data.kbList});
          }
          const doc = new DOMParser().parseFromString(text, 'text/html');
          const year = doc.querySelector('select#xnm'), semester = doc.querySelector('select#xqm');
          if (!year || !semester) {
            if (doc.querySelector('input[type="password"]')) throw 'SESSION_EXPIRED';
            throw 'INVALID_RESPONSE';
          }
          return year.outerHTML + semester.outerHTML;
        }
    """.trimIndent()
}
