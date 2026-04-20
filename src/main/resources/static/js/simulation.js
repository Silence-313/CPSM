// static/js/simulation.js
const { createApp, ref, onUnmounted } = Vue;

// 配置 marked
marked.setOptions({
    breaks: false,
    gfm: true,
});

const renderMarkdown = (text) => {
    if (!text) return '';
    let cleaned = text.replace(/\r\n|\r/g, '\n');
    cleaned = cleaned.replace(/\n{3,}/g, '\n\n');
    cleaned = cleaned.split('\n').map(line => line.trim()).join('\n');
    const rawHtml = marked.parse(cleaned);
    return DOMPurify.sanitize(rawHtml);
};

createApp({
    setup() {
        const eventDesc = ref('');
        const loading = ref(false);
        const sessionId = ref(null);
        const predictions = ref({});
        const verdict = ref('');
        const riskLevel = ref('');
        const history = ref([]);
        const showHistory = ref(false);
        let pollTimer = null;

        const startSimulation = async () => {
            if (!eventDesc.value.trim()) return alert('请输入模拟事件');
            loading.value = true;

            // --- 优化1：预初始化占位符 ---
            // 假设你通常有4个模型，先给用户视觉反馈。如果后端返回了具体名称，这里会被覆盖。
            predictions.value = { "专家模型A": "", "专家模型B": "", "专家模型C": "" };
            verdict.value = '';
            riskLevel.value = '';

            if (pollTimer) clearInterval(pollTimer);
            try {
                const res = await fetch('/api/simulation/start', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ event: eventDesc.value })
                });
                const data = await res.json();
                if (data.sessionId) {
                    sessionId.value = data.sessionId;
                    // 立即触发一次更新，让骨架屏更快变成真实模型名称
                    fetchStatus();
                    pollTimer = setInterval(fetchStatus, 2000);
                } else {
                    alert('启动失败：' + (data.error || '未知错误'));
                    loading.value = false;
                }
            } catch (e) {
                alert('网络错误，请稍后重试');
                loading.value = false;
            }
        };

        const fetchStatus = async () => {
            if (!sessionId.value) return;
            try {
                const res = await fetch(`/api/simulation/status/${sessionId.value}`);
                const data = await res.json(); // 修复：确保在此处定义 data
                predictions.value = data.predictions || {};
                if (data.complete) {
                    clearInterval(pollTimer);
                    pollTimer = null;
                    verdict.value = data.verdict;
                    riskLevel.value = data.riskLevel;
                    loading.value = false;
                    loadHistory();

                    // 交互增强：平滑滚动
                    setTimeout(() => {
                        const verdictEl = document.querySelector('.verdict-card');
                        if (verdictEl) verdictEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }, 300);
                }
            } catch (e) {
                console.error('获取状态失败', e);
            }

            if (data.complete) {
                clearInterval(pollTimer);
                pollTimer = null;
                verdict.value = data.verdict;
                riskLevel.value = data.riskLevel;
                loading.value = false;
                loadHistory();

                // 交互增强：自动平滑滚动到裁决区域
                setTimeout(() => {
                    const verdictEl = document.querySelector('.verdict-card');
                    if (verdictEl) verdictEl.scrollIntoView({ behavior: 'smooth' });
                }, 100);
            }
        };

        const loadHistory = async () => {
            try {
                const res = await fetch('/api/simulation/history');
                if (res.ok) history.value = await res.json();
            } catch (e) {
                console.error('加载历史失败', e);
            }
        };

        const viewHistory = async (id) => {
            try {
                const res = await fetch(`/api/simulation/status/${id}`);
                const data = await res.json();
                if (data.complete || data.predictions) {
                    sessionId.value = id;
                    predictions.value = data.predictions || {};
                    verdict.value = data.verdict || '';
                    riskLevel.value = data.riskLevel || 'UNKNOWN';
                    eventDesc.value = '';
                    if (pollTimer) {
                        clearInterval(pollTimer);
                        pollTimer = null;
                    }
                    loading.value = false;
                } else {
                    alert('无法加载历史记录');
                }
            } catch (e) {
                console.error('加载历史失败', e);
                alert('加载历史失败');
            }
        };

        const deleteHistory = async (id) => {
            if (!confirm('确定删除这条历史记录吗？')) return;
            try {
                const res = await fetch(`/api/simulation/history/${id}`, { method: 'DELETE' });
                if (res.ok) {
                    if (sessionId.value === id) {
                        sessionId.value = null;
                        predictions.value = {};
                        verdict.value = '';
                        riskLevel.value = '';
                    }
                    await loadHistory();
                } else {
                    alert('删除失败');
                }
            } catch (e) {
                console.error('删除失败', e);
            }
        };

        const clearAllHistory = async () => {
            if (!confirm('确定清空全部历史记录吗？此操作不可恢复。')) return;
            try {
                const res = await fetch('/api/simulation/history', { method: 'DELETE' });
                if (res.ok) {
                    sessionId.value = null;
                    predictions.value = {};
                    verdict.value = '';
                    riskLevel.value = '';
                    history.value = [];
                } else {
                    alert('清空失败');
                }
            } catch (e) {
                console.error('清空失败', e);
            }
        };

        loadHistory();
        onUnmounted(() => { if (pollTimer) clearInterval(pollTimer); });

        return {
            eventDesc, loading, sessionId, predictions,
            verdict, riskLevel, history, showHistory,
            startSimulation, viewHistory, renderMarkdown,
            deleteHistory, clearAllHistory,
        };
    }
}).mount('#app');