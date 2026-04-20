// monitor.js - 微博名人热度监测系统核心逻辑
const { createApp, ref, onMounted, onUnmounted } = Vue;

createApp({
    setup() {
        // ---------- 响应式数据 ----------
        const hotSearchList = ref([]);
        const monitorStars = ref([]);
        const loading = ref(false);
        const newStar = ref("");
        const autoRefresh = ref(false);
        let timer = null;

        // 颜色预设
        const colorPresets = [
            { name: '微博红', value: '#ff8200' },
            { name: '魅力紫', value: '#722ed1' },
            { name: '深海蓝', value: '#1890ff' },
            { name: '森林绿', value: '#52c41a' },
            { name: '酷炫黑', value: '#262626' }
        ];

        // ---------- API 请求方法 ----------
        const fetchData = async () => {
            loading.value = true;
            try {
                // 1. 获取热搜榜单
                const hotRes = await fetch('/api/weibo/monitor');
                if (hotRes.status === 401) {
                    window.location.href = 'login.html';
                    return;
                }
                if (!hotRes.ok) throw new Error('热搜数据获取失败');
                hotSearchList.value = await hotRes.json();

                // 2. 获取监测名单
                const monitorDetailRes = await fetch('/api/weibo/stars/MonitorDetail');
                if (!monitorDetailRes.ok) throw new Error('名单获取失败');

                // 3. 获取统计详情
                const detailRes = await fetch('/api/weibo/stars/detail');
                const monitorData = await monitorDetailRes.json();
                const voData = await detailRes.json();

                // 4. 合并数据渲染
                monitorStars.value = monitorData.map(ms => {
                    const vo = voData.find(v => v.name === ms.name);
                    return {
                        id: ms.id,
                        name: ms.name,
                        color: ms.color || '#ff8200',
                        keywords: Array.isArray(ms.keywords) ? [...new Set(ms.keywords)] : [],
                        totalHot: vo ? vo.totalHot : 0,
                        onBoardCount: ms.onBoardCount || 0
                    };
                });
            } catch (error) {
                console.error("同步失败:", error);
            } finally {
                loading.value = false;
            }
        };

        // 添加名人
        const addStar = async () => {
            if (!newStar.value.trim()) return;
            const defaultColor = colorPresets[0].value;
            try {
                const response = await fetch(`/api/weibo/star?name=${encodeURIComponent(newStar.value)}&color=${encodeURIComponent(defaultColor)}`, {
                    method: 'POST'
                });
                if (response.ok) {
                    await fetchData();
                    newStar.value = '';
                } else if (response.status === 400) {
                    alert("参数错误：请确保发送了 name 和 color");
                } else {
                    const errorMsg = await response.text();
                    alert("添加失败：" + errorMsg);
                }
            } catch (e) {
                console.error("添加失败", e);
                alert("网络错误，请稍后重试");
            }
        };

        // 移除名人
        const removeStar = async (name) => {
            if (!confirm(`不再监测 ${name} 吗？`)) return;
            try {
                await fetch(`/api/weibo/star?name=${encodeURIComponent(name)}`, { method: 'DELETE' });
                await fetchData();
            } catch (e) {
                console.error(e);
                alert("移除失败");
            }
        };

        // 删除关键词
        const deleteKeyword = async (starId, word) => {
            if (!confirm(`确定删除关键词 "${word}" 吗？`)) return;
            try {
                await fetch(`/api/weibo/star/keyword?starId=${starId}&keyword=${encodeURIComponent(word)}`, { method: 'DELETE' });
                await fetchData();
            } catch (e) {
                console.error("删除关键词失败", e);
                alert("删除失败");
            }
        };

        // 处理输入框回车添加关键词
        const handleKeywordEnter = async (starId, event) => {
            const inputElement = event.target;
            const kw = inputElement.value.trim();
            if (!kw) return;

            try {
                const res = await fetch(`/api/weibo/star/keyword?starId=${starId}&keyword=${encodeURIComponent(kw)}`, {
                    method: 'POST'
                });
                if (res.ok) {
                    inputElement.value = '';
                    await fetchData();
                } else {
                    alert("添加失败，请检查后端日志");
                }
            } catch (e) {
                console.error("添加关键词请求出错:", e);
                alert("网络错误");
            }
        };

        // 更新名人颜色
        const updateStarColor = async (star, newColor) => {
            const oldColor = star.color;
            star.color = newColor;  // 乐观更新

            try {
                const response = await fetch(`/api/weibo/star/updateColor?id=${star.id}&color=${encodeURIComponent(newColor)}`, {
                    method: 'POST'
                });
                if (!response.ok) throw new Error('保存失败');
                // 颜色保存成功后刷新数据确保同步
                await fetchData();
            } catch (e) {
                console.error("颜色更新失败:", e);
                star.color = oldColor;
                alert("颜色保存失败，请稍后重试");
            }
        };

        // ---------- 热搜匹配与高亮逻辑 ----------
        const getMatchedStar = (word) => {
            return monitorStars.value.find(s => {
                const nameMatch = s.name && word.includes(s.name);
                const kwMatch = s.keywords && s.keywords.some(kw => word.includes(kw));
                return nameMatch || kwMatch;
            });
        };

        const isMatched = (word) => !!getMatchedStar(word);

        const getMatchedValue = (word) => {
            const star = getMatchedStar(word);
            if (!star) return '';
            return star.keywords.find(kw => word.includes(kw)) || star.name;
        };

        const getMatchedColor = (word) => {
            return getMatchedStar(word)?.color || '#ff8200';
        };

        const getHighlightStyle = (word) => {
            const star = getMatchedStar(word);
            return star ? { backgroundColor: star.color + '15', borderLeft: `5px solid ${star.color}`, fontWeight: 'bold' } : {};
        };

        // ---------- 辅助方法 ----------
        const formatNum = (num) => num ? num.toLocaleString() : '0';

        // 自动刷新控制
        const toggleAutoRefresh = () => {
            if (autoRefresh.value) {
                timer = setInterval(fetchData, 60000);
            } else {
                if (timer) {
                    clearInterval(timer);
                    timer = null;
                }
            }
        };

        // ---------- 生命周期 ----------
        onMounted(() => {
            fetchData();
        });

        onUnmounted(() => {
            if (timer) clearInterval(timer);
        });

        return {
            hotSearchList,
            monitorStars,
            loading,
            newStar,
            autoRefresh,
            colorPresets,
            fetchData,
            formatNum,
            addStar,
            removeStar,
            toggleAutoRefresh,
            getHighlightStyle,
            isMatched,
            getMatchedColor,
            getMatchedValue,
            deleteKeyword,
            updateStarColor,
            handleKeywordEnter
        };
    }
}).mount('#app');