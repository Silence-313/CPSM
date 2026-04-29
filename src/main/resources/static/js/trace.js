const { createApp, ref, onMounted } = Vue;

createApp({
    setup() {
        const inputMid = ref('');
        const loading = ref(false);
        const graphData = ref(null);
        const selectedNode = ref(null);
        const aiAnalysis = ref('');
        const currentStep = ref(0);
        const timeSteps = ref([]);
        let chart = null;
        let pollTimer = null;
        const MAX_POLL = 30;

        // 从 URL 或文本中提取纯 MID
        const extractMid = (input) => {
            if (!input) return '';
            // 匹配 weibo.com/{uid}/{mid}?...
            const urlMatch = input.match(/weibo\.com\/\d+\/([a-zA-Z0-9]+)/);
            if (urlMatch) return urlMatch[1];
            // 匹配纯数字 MID（9-16位）
            const numMatch = input.match(/(\d{9,16})/);
            if (numMatch) return numMatch[1];
            // 已经是纯 MID
            return input.trim();
        };

        const startTrace = async () => {
            if (!inputMid.value) return alert('请输入微博ID或链接');
            const mid = extractMid(inputMid.value);
            if (!mid) return alert('无法识别微博ID，请输入正确的链接或ID');

            loading.value = true;
            graphData.value = null;
            if (pollTimer) clearInterval(pollTimer);

            try {
                // 触发抓取
                const buildRes = await fetch(`/api/trace/build?mid=${encodeURIComponent(mid)}`, { method: 'POST' });
                if (!buildRes.ok) {
                    alert('启动抓取失败');
                    loading.value = false;
                    return;
                }

                let pollCount = 0;
                const MAX_POLL = 60;

                pollTimer = setInterval(async () => {
                    pollCount++;
                    if (pollCount > MAX_POLL) {
                        clearInterval(pollTimer);
                        loading.value = false;
                        alert('抓取超时，请稍后重试或检查 Cookie 是否有效');
                        return;
                    }

                    try {
                        // 先查询任务状态
                        const statusRes = await fetch(`/api/trace/status/${mid}`);
                        const statusData = await statusRes.json();
                        const status = statusData.status;

                        if (status === 'FAILED') {
                            clearInterval(pollTimer);
                            loading.value = false;
                            alert('抓取失败，请查看后台日志');
                            return;
                        }

                        if (status === 'COMPLETED' || status === 'RUNNING') {
                            // 尝试获取树数据
                            const treeRes = await fetch(`/api/trace/tree/${mid}`);
                            if (treeRes.ok) {
                                const data = await treeRes.json();
                                if (data.nodes && data.nodes.length > 0) {
                                    graphData.value = data;
                                    clearInterval(pollTimer);
                                    renderChart(data);
                                    loading.value = false;
                                }
                            }
                        }
                    } catch (e) {
                        console.error('轮询失败', e);
                    }
                }, 2000);

            } catch (e) {
                alert('请求失败');
                loading.value = false;
            }
        };

        const renderChart = (data) => {
            const chartDom = document.getElementById('traceChart');
            if (!chartDom) return;
            chart = echarts.init(chartDom);

            // 提取并排序时间点
            const times = [...new Set(data.nodes.map(n => n.postTime).filter(t => t).sort())];
            timeSteps.value = times;

            const option = {
                title: { text: '传播路径溯源' },
                tooltip: {},
                series: [{
                    type: 'graph',
                    layout: 'force',
                    force: { repulsion: 200, edgeLength: 80 },
                    roam: true,
                    draggable: true,
                    data: data.nodes,
                    links: data.links,
                    lineStyle: { color: '#aaa', curveness: 0.2 },
                    label: { show: true, position: 'right', fontSize: 10 },
                    emphasis: { focus: 'adjacency' }
                }]
            };
            chart.setOption(option);
            chart.on('click', async (params) => {
                if (params.dataType === 'node') {
                    selectedNode.value = params.data;
                    try {
                        const res = await fetch(`/api/trace/analyze-node?mid=${params.data.id}`, { method: 'POST' });
                        aiAnalysis.value = await res.text();
                    } catch (e) {
                        aiAnalysis.value = 'AI分析请求失败';
                    }
                }
            });
        };

        const updateByTime = () => {
            if (!graphData.value || timeSteps.value.length === 0) return;
            const stepTime = timeSteps.value[currentStep.value];
            const filteredNodes = graphData.value.nodes.filter(n => n.postTime <= stepTime);
            const filteredNodeIds = new Set(filteredNodes.map(n => n.id));
            const filteredLinks = graphData.value.links.filter(l =>
                filteredNodeIds.has(l.source) && filteredNodeIds.has(l.target)
            );
            chart.setOption({
                series: [{
                    data: filteredNodes,
                    links: filteredLinks
                }]
            });
        };

        const mockData = () => {
            graphData.value = {
                nodes: [
                    { id: 'root', name: '源头微博', value: 100, sentiment: 0, postTime: '2025-04-01T10:00:00' },
                    { id: 'a1', name: '大V转发', value: 50, sentiment: 1, postTime: '2025-04-01T11:00:00' },
                    { id: 'a2', name: '路人甲', value: 5, sentiment: -1, postTime: '2025-04-01T11:30:00' },
                ],
                links: [
                    { source: 'root', target: 'a1' },
                    { source: 'a1', target: 'a2' }
                ]
            };
            renderChart(graphData.value);
        };

        onMounted(() => {
            // 可预加载模拟数据预览
        });

        return { inputMid, loading, graphData, selectedNode, aiAnalysis, currentStep, timeSteps,
            startTrace, updateByTime, mockData };
    }
}).mount('#app');