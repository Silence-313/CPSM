const { createApp, ref, computed, nextTick, onMounted } = Vue;

createApp({
    setup() {
        const weiboUrl = ref('');
        const loading = ref(false);
        const hasResult = ref(false);

        // 存储后端返回的所有评论数据 (包含text和sentiment)
        const allComments = ref([]);

        // 筛选类型: 'all', 'positive', 'neutral', 'negative'
        const filterType = ref('all');

        const progress = ref(0);
        const statusText = ref('准备启动爬虫引擎...');
        let sentimentChart = null;

        const goBack = () => window.location.href = 'portal.html';
        const openHelp = () => {
            window.open('help.html', '_blank');
        };
        // 情感阈值定义
        const POS_THRESHOLD = 0.3;
        const NEG_THRESHOLD = -0.3;

        // 根据分数获取情感类型
        const getSentimentType = (score) => {
            if (score > POS_THRESHOLD) return 'positive';
            if (score < NEG_THRESHOLD) return 'negative';
            return 'neutral';
        };

        // 统计各类型数量
        const stats = computed(() => {
            const pos = allComments.value.filter(c => c.sentiment > POS_THRESHOLD).length;
            const neg = allComments.value.filter(c => c.sentiment < NEG_THRESHOLD).length;
            const neu = allComments.value.length - pos - neg;
            return {
                all: allComments.value.length,
                positive: pos,
                neutral: neu,
                negative: neg
            };
        });

        // 根据筛选条件过滤评论
        const filteredComments = computed(() => {
            if (filterType.value === 'all') return allComments.value;
            return allComments.value.filter(c => getSentimentType(c.sentiment) === filterType.value);
        });

        // 获取情感显示标签
        const getSentimentLabel = (score) => {
            const type = getSentimentType(score);
            if (type === 'positive') return '正面';
            if (type === 'negative') return '负面';
            return '中性';
        };

        // 获取情感标签样式类
        const getSentimentClass = (score) => {
            const type = getSentimentType(score);
            return {
                'sentiment-positive': type === 'positive',
                'sentiment-neutral': type === 'neutral',
                'sentiment-negative': type === 'negative'
            };
        };

        // 初始化/更新饼图
        const initSentimentChart = (data) => {
            const chartDom = document.getElementById('sentimentChart');
            if (!sentimentChart) {
                sentimentChart = echarts.init(chartDom);
            }
            sentimentChart.setOption({
                title: { text: '情感比例', left: 'center', top: 'center' },
                tooltip: { trigger: 'item' },
                color: ['#52c41a', '#faad14', '#f5222d'],
                series: [{
                    name: '评论倾向',
                    type: 'pie',
                    radius: ['40%', '70%'],
                    avoidLabelOverlap: false,
                    itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                    label: { show: true, formatter: '{b}: {c}' },
                    data: data
                }]
            });
        };

        const deleteHistoryItem = async (mid) => {
            if (!confirm('确定删除这条历史记录吗？')) return;
            try {
                const res = await fetch(`/api/analysis/history/${encodeURIComponent(mid)}`, {
                    method: 'DELETE'
                });
                if (res.status === 401) {
                    window.location.href = 'login.html';
                    return;
                }
                if (res.ok) {
                    // 刷新历史列表
                    await fetchHistory();
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
                const res = await fetch('/api/analysis/history', {
                    method: 'DELETE'
                });
                if (res.status === 401) {
                    window.location.href = 'login.html';
                    return;
                }
                if (res.ok) {
                    historyList.value = [];
                } else {
                    alert('清空失败');
                }
            } catch (e) {
                console.error('清空失败', e);
            }
        };

        const initWordCloud = (data) => {
            const chart = echarts.init(document.getElementById('wordCloudChart'));
            chart.setOption({
                series: [{
                    type: 'wordCloud',
                    shape: 'circle',
                    gridSize: 8,
                    sizeRange: [12, 50],
                    rotationRange: [-45, 90],
                    textStyle: {
                        fontFamily: 'sans-serif',
                        fontWeight: 'bold',
                        color: () => `rgb(${Math.round(Math.random()*160)}, ${Math.round(Math.random()*160)}, 160)`
                    },
                    data: data
                }]
            });
        };

        const startAnalysis = async () => {
            if (!weiboUrl.value) return alert('请输入微博链接或 ID');

            loading.value = true;
            hasResult.value = false;
            progress.value = 0;

            const progressTimer = setInterval(async () => {
                try {
                    const res = await fetch(`/api/analysis/progress?mid=${encodeURIComponent(weiboUrl.value)}`);
                    if (res.ok) {
                        const val = await res.json();
                        progress.value = val;
                        if (val < 20) statusText.value = "初始化爬虫环境...";
                        else if (val < 55) statusText.value = "正在深度并发抓取历史评论...";
                        else if (val < 95) statusText.value = "正在清洗无效数据并进行分词...";
                        else statusText.value = "即将生成分析报告...";
                    }
                } catch (e) {
                    console.error("轮询失败", e);
                }
            }, 800);

            try {
                const response = await fetch('/api/analysis/weibo', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mid: weiboUrl.value })
                });

                if (response.status === 401) {
                    window.location.href = 'login.html';
                    return;
                }

                const resData = await response.json();
                if (resData.errorMsg) {
                    alert('失败：' + resData.errorMsg);
                }else {
                    hasResult.value = true;
                    // 保存所有评论数据
                    allComments.value = resData.topComments || [];
                    // 默认展示全部
                    filterType.value = 'all';

                    await nextTick();

                    initSentimentChart([
                        { value: resData.positive, name: '正面' },
                        { value: resData.neutral, name: '中性' },
                        { value: resData.negative, name: '负面' }
                    ]);

                    if (resData.wordCloudData) {
                        initWordCloud(resData.wordCloudData);
                    }
                }
            } catch (error) {
                console.error('Error:', error);
            } finally {
                clearInterval(progressTimer);
                loading.value = false;
                progress.value = 100;
            }
        };

        const showHistory = ref(false);
        const historyList = ref([]);
        const historyLoading = ref(false);

        const fetchHistory = async () => {
            historyLoading.value = true;
            try {
                const res = await fetch('/api/analysis/history');
                if (res.ok) {
                    historyList.value = await res.json();
                } else if (res.status === 401) {
                    window.location.href = 'login.html';
                }
            } catch (e) {
                console.error('获取历史失败', e);
            } finally {
                historyLoading.value = false;
            }
        };

        const formatTime = (timeStr) => {
            if (!timeStr) return '';
            const date = new Date(timeStr);
            return `${date.getMonth()+1}-${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2,'0')}`;
        };

        const viewHistory = (item) => {
            // 弹出选择对话框
            const action = confirm(
                `微博ID: ${item.mid}\n` +
                `分析时间: ${formatTime(item.lastAnalyzeTime)}\n` +
                `正面: ${item.positiveCount}  中性: ${item.neutralCount}  负面: ${item.negativeCount}\n\n` +
                `点击"确定"仅查看上次统计结果\n点击"取消"重新抓取分析`
            );

            if (action) {
                // 确定：仅展示历史统计（不重新抓取）
                showHistoryResult(item);
            } else {
                // 取消：重新分析
                weiboUrl.value = item.mid;
                showHistory.value = false;
                // 自动触发分析
                startAnalysis();
            }
        };
        const showHistoryResult = async (item) => {
            loading.value = true;
            try {
                const res = await fetch(`/api/analysis/history/detail?mid=${item.mid}`);
                if (res.status === 401) {
                    window.location.href = 'login.html';
                    return;
                }
                if (!res.ok) {
                    alert('获取详情失败');
                    return;
                }
                const detail = await res.json();

                hasResult.value = true;
                allComments.value = detail.comments || [];
                filterType.value = 'all';
                weiboUrl.value = detail.mid;

                await nextTick();
                initSentimentChart([
                    { value: detail.positiveCount, name: '正面' },
                    { value: detail.neutralCount, name: '中性' },
                    { value: detail.negativeCount, name: '负面' }
                ]);
                if (detail.wordCloudData) {
                    initWordCloud(detail.wordCloudData);
                }
            } catch (e) {
                console.error(e);
                alert('加载历史详情失败');
            } finally {
                loading.value = false;
                showHistory.value = false;
            }
        };

        onMounted(() => {
            fetchHistory();
        });


        return {
            weiboUrl, loading, hasResult, allComments,
            progress, statusText, filterType,
            stats, filteredComments,
            getSentimentLabel, getSentimentClass,
            startAnalysis, goBack, openHelp,
            showHistory, historyList, historyLoading,
            fetchHistory, formatTime, viewHistory, showHistoryResult,
            deleteHistoryItem, clearAllHistory

        };
    }
}).mount('#app');