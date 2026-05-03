const { createApp, ref, reactive, onMounted } = Vue;

createApp({
    setup() {
        const username = ref(localStorage.getItem('currentUser') || '用户');

        const dpData = reactive({
            cpuLoad: '--',
            memoryUsage: '--',
            qps: '--',
            neo4jOk: true
        });

        const goTo = (path) => {
            window.location.href = path;
        };

        const logout = () => {
            localStorage.removeItem('isLoggedIn');
            window.location.href = 'login.html';
        };

        // 加载 Dashboard 预览数据
        const fetchDashboardPreview = async () => {
            try {
                const res = await fetch('/api/monitor/dashboard-data');
                const json = await res.json();
                dpData.cpuLoad = (json.system.cpuLoad || 0).toFixed(1);
                dpData.memoryUsage = (json.system.jvmMemoryUsage || 0).toFixed(1);
                dpData.qps = (json.api?.qps || json.apiInfo?.qps || 0).toFixed(1);
                dpData.neo4jOk = json.database.neo4jConnected;
            } catch (e) {
                console.error('Dashboard preview fetch error:', e);
            }
        };

        onMounted(() => {
            fetchDashboardPreview();
            setInterval(fetchDashboardPreview, 10000);
        });

        return { username, dpData, goTo, logout };
    }
}).mount('#app');
