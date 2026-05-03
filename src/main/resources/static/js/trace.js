const { createApp, ref, onMounted, nextTick } = Vue;

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
        let processedData = null;  // 经 renderChart 处理后的 ECharts 数据

        const startTrace = async () => {
            if (!inputMid.value) return alert('请输入微博ID或链接');

            loading.value = true;
            graphData.value = null;
            timeSteps.value = [];
            currentStep.value = 0;
            if (pollTimer) clearInterval(pollTimer);

            try {
                // 发送原始输入到后端，由后端 WeiboIdParser 统一解析
                const buildRes = await fetch(`/api/trace/build?mid=${encodeURIComponent(inputMid.value.trim())}`, { method: 'POST' });
                const buildData = await buildRes.json();

                if (!buildData.success) {
                    alert(buildData.message || '无法识别微博ID，请检查输入');
                    loading.value = false;
                    return;
                }

                // 使用后端返回的解析后 MID
                const mid = buildData.mid;
                if (!mid) {
                    alert('后端解析MID失败');
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
                                    await nextTick();
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
            if (chart) {
              CPMSTheme.unregisterChart(chart);
              chart.dispose();
            }
            chart = echarts.init(chartDom);

            // 提取并排序时间点
            const times = [...new Set(data.nodes.map(n => n.postTime).filter(t => t).sort())];
            timeSteps.value = times.length > 0 ? times : [''];

            const nodeCount = data.nodes.length;

            // 根节点 ID（depth=0 的节点）
            const rootNode = data.nodes.find(n => n.depth === 0);
            const rootId = rootNode ? rootNode.id : null;

            // --- 根据节点数自适应力参数 ---
            const repulsion = nodeCount < 15 ? 300 : nodeCount < 30 ? 500 : 800;
            const edgeLength = nodeCount < 15 ? 100 : nodeCount < 30 ? 130 : 180;
            const gravity = 0.15;

            // --- 深度对应的颜色 ---
            const depthColors = [
                '#ff8200',  // 0 = 根节点（橙色）
                '#1890ff',  // 1 = 直接转发（蓝色）
                '#52c41a',  // 2 = 二级转发（绿色）
                '#722ed1',  // 3 = 三级转发（紫色）
                '#fa8c16',  // 4 = 四级转发（橘色）
                '#13c2c2',  // 5 = 五级转发（青色）
            ];

            // --- 构造节点数据 ---
            const nodes = data.nodes.map(n => {
                const depth = n.depth || 0;
                const color = depthColors[Math.min(depth, depthColors.length - 1)];
                // 情感覆盖：如果有显著情感，用红/绿色
                let finalColor = color;
                if (n.sentiment === 1) finalColor = '#52c41a';
                else if (n.sentiment === -1) finalColor = '#f5222d';

                // 标签：只对根节点和转发数>0的重要节点显示
                const showLabel = depth === 0 || (n.value && n.value > 0);

                // 符号大小：根节点最大，逐层缩小
                const baseSize = depth === 0 ? 40 : Math.max(15, 30 - depth * 5);
                const valSize = n.value ? Math.sqrt(n.value) * 6 : 0;
                const symbolSize = Math.max(baseSize, baseSize + valSize);

                return {
                    id: n.id,
                    name: n.name || n.id,
                    value: n.value || 0,
                    depth: depth,
                    sentiment: n.sentiment || 0,
                    postTime: n.postTime,
                    symbolSize: symbolSize,
                    itemStyle: { color: finalColor },
                    label: {
                        show: showLabel,
                        position: depth === 0 ? 'inside' : 'right',
                        fontSize: depth === 0 ? 12 : 10,
                        fontWeight: depth === 0 ? 'bold' : 'normal',
                        formatter: (p) => p.name.length > 6 ? p.name.slice(0, 6) + '..' : p.name
                    },
                };
            });

            // --- 构造连线数据 ---
            const links = data.links.map(l => ({
                source: l.source,
                target: l.target,
                lineStyle: { color: '#c0c4cc', width: 1, curveness: 0.15 },
            }));

            // 缓存处理后的数据供 updateByTime 使用
            processedData = { nodes, links };

            const option = {
                title: {
                    text: '传播路径溯源',
                    subtext: `共 ${nodeCount} 个节点`,
                    left: 'center',
                },
                tooltip: {
                    formatter: (p) => {
                        if (p.dataType === 'node') {
                            const d = p.data;
                            return `<b>${d.name}</b><br/>
                              层级：第${d.depth}层<br/>
                              转发数：${d.value}<br/>
                              情感：${d.sentiment === 1 ? '正面' : d.sentiment === -1 ? '负面' : '中性'}<br/>
                              时间：${d.postTime || '未知'}`;
                        }
                        return '';
                    },
                },
                toolbox: {
                    show: true,
                    feature: {
                        restore: { title: '重置' },
                        saveAsImage: { title: '保存图片', pixelRatio: 2 },
                    },
                },
                series: [{
                    type: 'graph',
                    layout: 'force',
                    roam: true,
                    draggable: true,
                    animation: true,
                    animationDuration: 1500,
                    animationEasingUpdate: 'quinticInOut',
                    force: {
                        initIterations: 200,
                        repulsion: repulsion,
                        edgeLength: edgeLength,
                        gravity: gravity,
                        friction: 0.6,
                    },
                    data: nodes,
                    links: links,
                    categories: [
                        { name: '源头' },
                        { name: '一级转发' },
                        { name: '二级转发' },
                        { name: '三级转发' },
                        { name: '更深层级' },
                    ],
                    roam: true,
                    draggable: true,
                    scaleLimit: { min: 0.3, max: 5 },
                    label: {
                        show: true,
                        position: 'right',
                        fontSize: 10,
                    },
                    emphasis: {
                        focus: 'adjacency',
                        label: { show: true, fontSize: 12, fontWeight: 'bold' },
                        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' },
                        lineStyle: { width: 2 },
                    },
                    edgeSymbol: ['none', 'arrow'],
                    edgeSymbolSize: [0, 6],
                }]
            };

            chart.setOption(option);

            // Register for theme switching
            CPMSTheme.registerChart(chart, function(tokens) {
              return {
                backgroundColor: 'transparent',
                textStyle: { color: tokens.chartText },
                title: { textStyle: { color: tokens.chartText } }
              };
            });

            // 自动居中到根节点
            if (rootId) {
                setTimeout(() => {
                    chart.dispatchAction({ type: 'highlight', seriesIndex: 0 });
                    chart.dispatchAction({ type: 'downplay', seriesIndex: 0 });
                }, 1600);
            }

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
            if (!processedData || timeSteps.value.length === 0 || !timeSteps.value[0]) return;
            const stepTime = timeSteps.value[currentStep.value];
            const filteredNodes = processedData.nodes.filter(n => n.postTime <= stepTime);
            const filteredNodeIds = new Set(filteredNodes.map(n => n.id));
            const filteredLinks = processedData.links.filter(l =>
                filteredNodeIds.has(l.source) && filteredNodeIds.has(l.target)
            );
            chart.setOption({
                series: [{
                    data: filteredNodes,
                    links: filteredLinks
                }]
            });
        };

        const mockData = async () => {
            graphData.value = {
                nodes: [
                    { id: 'root', name: '源头微博', value: 100, sentiment: 0, depth: 0, postTime: '2025-04-01T10:00:00' },
                    { id: 'a1', name: '大V转发', value: 50, sentiment: 1, depth: 1, postTime: '2025-04-01T11:00:00' },
                    { id: 'a2', name: '路人甲', value: 5, sentiment: -1, depth: 2, postTime: '2025-04-01T11:30:00' },
                ],
                links: [
                    { source: 'root', target: 'a1' },
                    { source: 'a1', target: 'a2' }
                ]
            };
            await nextTick();
            renderChart(graphData.value);
        };

        onMounted(() => {
            // 可预加载模拟数据预览
        });

        return { inputMid, loading, graphData, selectedNode, aiAnalysis, currentStep, timeSteps,
            startTrace, updateByTime, mockData };
    }
}).mount('#app');