package com.example.util;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class HtmlUtil {

    // 마크다운을 HTML로 변환 (Java 처리)
    public static String markdownToHtml(String markdown) {
        if (markdown == null) return "";
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(document);
    }

    public static String getSkeletonHtml() {
        return "<!DOCTYPE html>" +
                "<html lang='ko'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                // Base styles (Dark Theme)
                "body { font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; background-color: #121212; color: #E0E0E0; padding: 20px; margin: 0; }" +
                ".message-container { display: flex; flex-direction: column; gap: 15px; padding-bottom: 50px; }" +
                
                // User Message Wrapper & Style
                ".user-wrapper { display: flex; flex-direction: column; align-items: flex-end; max-width: 85%; align-self: flex-end; }" +
                ".user-msg { background-color: #1B5E20; color: #E8F5E9; padding: 12px 18px; border-radius: 18px 18px 0 18px; box-shadow: 0 1px 2px rgba(0,0,0,0.5); font-size: 14px; line-height: 1.5; width: fit-content; }" +
                
                // AI Message Wrapper & Style
                ".ai-wrapper { display: flex; flex-direction: column; align-items: flex-start; max-width: 85%; }" +
                ".ai-msg { background-color: #333333; color: #FAFAFA; padding: 15px 20px; border-radius: 18px 18px 18px 0; border: 1px solid #444444; box-shadow: 0 1px 2px rgba(0,0,0,0.5); font-size: 14px; line-height: 1.6; width: 100%; box-sizing: border-box; }" +
                
                // [NEW] Message Actions (Copy/Edit)
                ".msg-actions { display: flex; gap: 5px; margin-top: 4px; opacity: 0; transition: opacity 0.2s; padding: 0 5px; }" +
                ".ai-wrapper:hover .msg-actions, .user-wrapper:hover .msg-actions { opacity: 1; }" + 
                ".action-btn { background: none; border: none; color: #757575; cursor: pointer; font-size: 11px; padding: 4px 6px; border-radius: 4px; transition: all 0.2s; font-family: 'Malgun Gothic'; }" +
                ".action-btn:hover { background-color: #424242; color: #E0E0E0; }" +

                // [NEW] Edit Mode Styles
                ".edit-area { width: 100%; box-sizing: border-box; background-color: #212121; color: #E0E0E0; border: 1px solid #4CAF50; border-radius: 8px; padding: 10px; font-family: 'Consolas', monospace; font-size: 13px; min-height: 60px; resize: vertical; margin-bottom: 5px; }" +
                ".edit-controls { display: flex; justify-content: flex-end; gap: 8px; }" +
                ".btn-save { background-color: #2E7D32; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; }" +
                ".btn-cancel { background-color: #424242; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; }" +

                // System Message
                ".system-wrapper { display: flex; justify-content: center; width: 100%; }" +
                ".system-msg { background-color: #212121; color: #9E9E9E; padding: 8px 15px; border-radius: 20px; font-size: 12px; border: 1px solid #424242; }" +
                
                // Markdown Styles (Dark)
                ".ai-msg code { background-color: #424242; padding: 2px 5px; border-radius: 4px; font-family: 'Consolas', monospace; color: #FF80AB; }" +
                ".ai-msg p { margin: 0 0 10px 0; }" +
                ".ai-msg p:last-child { margin-bottom: 0; }" +
                ".code-wrapper { margin: 10px 0; border: 1px solid #444; border-radius: 8px; overflow: hidden; background-color: #1E1E1E; }" +
                ".code-header { display: flex; justify-content: space-between; align-items: center; background-color: #2D2D2D; padding: 6px 12px; border-bottom: 1px solid #444; }" +
                ".code-lang { font-family: 'Consolas', sans-serif; font-size: 12px; color: #AAAAAA; font-weight: bold; text-transform: uppercase; }" +
                ".code-actions { display: flex; gap: 10px; }" +
                ".code-btn { background: none; border: none; color: #BBBBBB; cursor: pointer; font-size: 12px; display: flex; align-items: center; gap: 4px; padding: 2px 5px; border-radius: 4px; transition: background 0.2s; }" +
                ".code-btn:hover { background-color: #444; color: #FFFFFF; }" +
                ".ai-msg pre { margin: 0 !important; border: none !important; border-radius: 0 !important; background-color: #1E1E1E !important; padding: 15px; overflow-x: auto; }" +
                ".ai-msg pre code { background-color: transparent; color: #D4D4D4; }" +
                ".code-content.collapsed { display: none; }" +
                // Approval Box Styles
                ".approval-container { margin-top: 15px; background-color: #1E1E1E; border: 1px solid #FFD54F; color: #E0E0E0; padding: 12px; border-radius: 8px; display: flex; flex-direction: column; gap: 8px; }" +
                ".approval-result { width: 100%; box-sizing: border-box; background-color: #000000; color: #00FF00; padding: 10px; border-radius: 5px; font-family: 'Consolas', monospace; font-size: 12px; margin-top: 10px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow-y: auto; }" +
                ".status-running { color: #FFF176; font-weight: bold; font-style: italic; }" +
                ".approval-content { font-size: 13px; line-height: 1.4; font-family: 'Consolas', monospace; color: #FFF59D; word-break: break-all; }" + 
                ".approval-buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 5px; }" +
                ".btn-approve { background-color: #2E7D32; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; font-family: 'Malgun Gothic'; transition: background 0.2s; }" +
                ".btn-approve:hover { background-color: #1B5E20; }" +
                ".btn-reject { background-color: #C62828; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; font-family: 'Malgun Gothic'; transition: background 0.2s; }" +
                ".btn-reject:hover { background-color: #B71C1C; }" +
                
                "</style>" +
                "<script>" +
                "  let currentAiDiv = null;" +
                "  let rawAiText = '';" +
                "" +
                "  function scrollToBottom() { window.scrollTo(0, document.body.scrollHeight); }" +
                "" +
                "  function appendUserMessage(text, msgId) {" +
                "      const container = document.getElementById('chat-container');" +
                "      const wrapper = document.createElement('div');" +
                "      wrapper.className = 'user-wrapper';" +
                "      wrapper.dataset.id = msgId;" + 
                "      const msgDiv = document.createElement('div');" +
                "      msgDiv.className = 'user-msg';" +
                "      msgDiv.innerText = text;" +
                "      wrapper.appendChild(msgDiv);" +
                "      addMessageActions(wrapper, msgId);" + 
                "      container.appendChild(wrapper);" +
                "      scrollToBottom();" +
                "  }" +
                "" +
                "  function appendSystemMessage(text) {" +
                "      const container = document.getElementById('chat-container');" +
                "      const wrapper = document.createElement('div');" +
                "      wrapper.className = 'system-wrapper';" +
                "      const msgDiv = document.createElement('div');" +
                "      msgDiv.className = 'system-msg';" +
                "      msgDiv.innerText = text;" +
                "      wrapper.appendChild(msgDiv);" +
                "      container.appendChild(wrapper);" +
                "      scrollToBottom();" +
                "  }" +
                "" +
                "  function startAiMessage(msgId) {" +
                "      const container = document.getElementById('chat-container');" +
                "      const wrapper = document.createElement('div');" +
                "      wrapper.className = 'ai-wrapper';" +
                "      wrapper.dataset.id = msgId;" +
                "      currentAiDiv = document.createElement('div');" +
                "      currentAiDiv.className = 'ai-msg';" +
                "      wrapper.appendChild(currentAiDiv);" +
                "      container.appendChild(wrapper);" +
                "      rawAiText = '';" +
                "      scrollToBottom();" +
                "  }" +
                "" +
                "  function streamAiToken(token) {" +
                "      if (!currentAiDiv) return;" +
                "      rawAiText += token;" +
                "      currentAiDiv.innerText = rawAiText;" +
                "      scrollToBottom();" +
                "  }" +
                "" +
                "  function finishAiMessage() {" +
                "      currentAiDiv = null;" +
                "      rawAiText = '';" +
                "      setTimeout(enhanceCodeBlocks, 50);" + 
                "      scrollToBottom();" +
                "  }" +
                "" +
                "  function updateLastAiHtml(htmlContent, msgId) {" +
                "      const container = document.getElementById('chat-container');" +
                "      const wrappers = container.getElementsByClassName('ai-wrapper');" +
                "      if (wrappers.length > 0) {" +
                "          const lastWrapper = wrappers[wrappers.length - 1];" +
                "          const msgDiv = lastWrapper.getElementsByClassName('ai-msg')[0];" +
                "          if (msgDiv) {" +
                "              msgDiv.innerHTML = htmlContent;" +
                "              lastWrapper.dataset.id = msgId;" + 
                "              addMessageActions(lastWrapper, msgId);" + 
                "              enhanceCodeBlocks();" +
                "          }" +
                "      }" +
                "  }" +
                "" +
                // [NEW] 메시지 하단 버튼 (복사 / 수정) 추가
                "  function addMessageActions(wrapper, msgId) {" +
                "      if (wrapper.querySelector('.msg-actions')) return;" +
                "      const actionsDiv = document.createElement('div');" +
                "      actionsDiv.className = 'msg-actions';" +
                "      actionsDiv.innerHTML = " +
                "          '<button class=\"action-btn\" onclick=\"copyMessage(\\'' + msgId + '\\')\">복사</button>' +" +
                "          '<button class=\"action-btn\" onclick=\"editMessage(this, \\'' + msgId + '\\')\">수정</button>';" +
                "      wrapper.appendChild(actionsDiv);" +
                "  }" +
                "" +
                // [NEW] 복사 기능
                "  function copyMessage(id) {" +
                "      app.copyMessage(id);" +
                "  }" +
                "" +
                // [NEW] 수정 모드 진입
                "  function editMessage(btn, id) {" +
                "      const wrapper = btn.closest('.ai-wrapper') || btn.closest('.user-wrapper');" +
                "      const msgDiv = wrapper.querySelector('.ai-msg') || wrapper.querySelector('.user-msg');" +
                "      if (!msgDiv) return;" +
                "      " +
                "      const currentHtml = msgDiv.innerHTML;" +
                "      const rawContent = app.getMessageContent(id);" + 
                "      " +
                "      if (msgDiv.querySelector('.edit-area')) return;" +
                "      " +
                "      msgDiv.innerHTML = '';" + 
                "      const textarea = document.createElement('textarea');" +
                "      textarea.className = 'edit-area';" +
                "      textarea.value = rawContent;" +
                "      " +
                "      const controls = document.createElement('div');" +
                "      controls.className = 'edit-controls';" +
                "      controls.innerHTML = " +
                "          '<button class=\"btn-save\" onclick=\"saveEdit(\\'' + id + '\\')\">저장</button>' +" +
                "          '<button class=\"btn-cancel\" onclick=\"cancelEdit(this)\">취소</button>';" +
                "      " +
                "      textarea.dataset.originalHtml = currentHtml;" +
                "      " +
                "      msgDiv.appendChild(textarea);" +
                "      msgDiv.appendChild(controls);" +
                "      " +
                "      textarea.style.height = 'auto';" +
                "      textarea.style.height = (textarea.scrollHeight + 10) + 'px';" +
                "  }" +
                "" +
                // [NEW] 수정 취소
                "  function cancelEdit(btn) {" +
                "      const wrapper = btn.closest('.ai-msg') || btn.closest('.user-msg');" +
                "      const textarea = wrapper.querySelector('.edit-area');" +
                "      const originalHtml = textarea.dataset.originalHtml;" +
                "      wrapper.innerHTML = originalHtml;" +
                "  }" +
                "" +
                // [NEW] 수정 저장
                "  function saveEdit(id) {" +
                "      const textarea = document.querySelector('.edit-area');" + // 현재 열려있는 수정창 (하나라고 가정)
                "      if (!textarea) return;" +
                "      const newContent = textarea.value;" +
                "      app.updateMessageContent(id, newContent);" +
                "  }" +
                "" +
                // [NEW] 수정 후 화면 갱신 (Java에서 호출)
                "  function refreshMessage(id, newHtml) {" +
                "      const wrapper = document.querySelector('div[data-id=\"' + id + '\"]');" +
                "      if (!wrapper) return;" +
                "      const msgDiv = wrapper.querySelector('.ai-msg') || wrapper.querySelector('.user-msg');" +
                "      if (msgDiv) {" +
                "          msgDiv.innerHTML = newHtml;" +
                "          addMessageActions(wrapper, id);" + 
                "          enhanceCodeBlocks();" +
                "      }" +
                "  }" +
                "" +
                // --- [코드 블록 꾸미기 로직] ---
                "  function enhanceCodeBlocks() {" +
                "      const pres = document.querySelectorAll('pre:not(.enhanced)');" +
                "      " +
                "      pres.forEach(function(pre) {" +
                "          if (pre.closest('.code-wrapper')) return;" + 
                "          " +
                "          pre.classList.add('enhanced');" +
                "          pre.classList.add('code-content');" +
                "          " +
                "          let lang = 'TEXT';" +
                "          const codeElement = pre.querySelector('code');" +
                "          if (codeElement) {" +
                "              codeElement.className.split(' ').forEach(function(cls) {" +
                "                  if (cls.startsWith('language-')) {" +
                "                      lang = cls.replace('language-', '').toUpperCase();" +
                "                  }" +
                "              });" +
                "          }" +
                "          " +
                "          const wrapper = document.createElement('div');" +
                "          wrapper.className = 'code-wrapper';" +
                "          " +
                "          const header = document.createElement('div');" +
                "          header.className = 'code-header';" +
                "          " +
                "          header.innerHTML = " +
                "              '<span class=\"code-lang\">' + lang + '</span>' +" +
                "              '<div class=\"code-actions\">' +" +
                "                  '<button class=\"code-btn\" onclick=\"copyCode(this)\">복사</button>' +" +
                "                  '<button class=\"code-btn\" onclick=\"toggleCode(this)\">▲ 접기</button>' +" +
                "              '</div>';" +
                "          " +
                "          pre.parentNode.insertBefore(wrapper, pre);" +
                "          wrapper.appendChild(header);" +
                "          wrapper.appendChild(pre);" +
                "      });" +
                "  }" +
                "" +
                "  function copyCode(btn) {" +
                "      const wrapper = btn.closest('.code-wrapper');" +
                "      const code = wrapper.querySelector('code').innerText;" +
                "      const textarea = document.createElement('textarea');" +
                "      textarea.value = code;" +
                "      document.body.appendChild(textarea);" +
                "      textarea.select();" +
                "      document.execCommand('copy');" +
                "      document.body.removeChild(textarea);" +
                "      const originalText = btn.innerText;" +
                "      btn.innerText = '완료';" +
                "      setTimeout(function() { btn.innerText = originalText; }, 1500);" +
                "  }" +
                "" +
                "  function toggleCode(btn) {" +
                "      const wrapper = btn.closest('.code-wrapper');" +
                "      const content = wrapper.querySelector('.code-content');" +
                "      if (content.style.display === 'none') {" +
                "          content.style.display = 'block';" +
                "          btn.innerText = '▲ 접기';" +
                "      } else {" +
                "          content.style.display = 'none';" +
                "          btn.innerText = '▼ 펴기';" +
                "      }" +
                "  }" +
                "" +
                // --- [Approval Box Functions] ---
                "  function showApprovalBox(message) {" +
                "      const wrappers = document.getElementsByClassName('ai-wrapper');" +
                "      if (wrappers.length === 0) return;" +
                "      const lastWrapper = wrappers[wrappers.length - 1];" +
                "      const aiMsgDiv = lastWrapper.getElementsByClassName('ai-msg')[0];" +
                "      const div = document.createElement('div');" +
                "      div.id = 'approval-box';" +
                "      div.className = 'approval-container';" +
                "      div.innerHTML = " +
                "          \"<div class='approval-content'>\" + message + \"</div>\" +" +
                "          \"<div class='approval-buttons'>\" +" +
                "              \"<button onclick='app.approve()' class='btn-approve'>✅ 승인</button>\" +" +
                "              \"<button onclick='app.reject()' class='btn-reject'>❌ 거절</button>\" +" +
                "          \"</div>\";" +
                "      aiMsgDiv.appendChild(div);" +
                "      scrollToBottom();" +
                "  }" +
                "  function removeApprovalBox() { const div = document.getElementById('approval-box'); if (div) div.remove(); }" +
                "  function setApprovalRunning() { const div = document.getElementById('approval-box'); if (!div) return; const btnDiv = div.getElementsByClassName('approval-buttons')[0]; btnDiv.innerHTML = '<span class=\"status-running\">⏳ 도구를 실행하고 있습니다...</span>'; }" +
                "  function updateApprovalResult(output, isSuccess) { const div = document.getElementById('approval-box'); if (!div) return; const btnDiv = div.getElementsByClassName('approval-buttons')[0]; btnDiv.style.display = 'block'; btnDiv.style.width = '100%'; const color = isSuccess ? '#00FF00' : '#FF5252'; btnDiv.innerHTML = '<div class=\"approval-result\" style=\"color:' + color + '\">' + output + '</div>'; div.id = 'approval-box-done'; scrollToBottom(); }" +
                "" +
                // --- [자동 감시자 (MutationObserver)] ---
                "  const observer = new MutationObserver(function(mutations) {" +
                "      enhanceCodeBlocks();" +
                "  });" +
                "  observer.observe(document.body, { childList: true, subtree: true });" +

                "</script>"+
                "</head>" +
                "<body>" +
                "<div id='chat-container' class='message-container'>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}