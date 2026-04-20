// help.js - 帮助页面交互脚本（预留功能，当前仅作为占位，保持结构完整）
(function() {
    // 页面加载完成后执行的初始化
    document.addEventListener('DOMContentLoaded', function() {
        // 可为关闭链接增加确认或日志（可选）
        const closeLink = document.querySelector('.back-link');
        if (closeLink) {
            closeLink.addEventListener('click', function(e) {
                // 原有关闭行为由 href="javascript:window.close();" 处理，此处仅用于扩展
                // 如果浏览器不允许脚本关闭窗口，可提供友好提示
                if (typeof window.close !== 'function') {
                    e.preventDefault();
                    alert('请手动关闭此标签页返回分析页面');
                }
            });
        }

        // 可添加其他轻量交互，如复制示例链接等，提升用户体验
        const exampleDivs = document.querySelectorAll('.url-example');
        exampleDivs.forEach(div => {
            div.style.cursor = 'pointer';
            div.addEventListener('click', function() {
                const text = this.innerText;
                // 尝试复制链接到剪贴板
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(text).then(() => {
                        const toast = document.createElement('div');
                        toast.textContent = '✅ 已复制示例链接';
                        toast.style.position = 'fixed';
                        toast.style.bottom = '20px';
                        toast.style.left = '50%';
                        toast.style.transform = 'translateX(-50%)';
                        toast.style.backgroundColor = 'rgba(0,0,0,0.7)';
                        toast.style.color = 'white';
                        toast.style.padding = '8px 16px';
                        toast.style.borderRadius = '30px';
                        toast.style.fontSize = '14px';
                        toast.style.zIndex = '9999';
                        document.body.appendChild(toast);
                        setTimeout(() => toast.remove(), 2000);
                    }).catch(() => {});
                }
            });
        });
    });
})();