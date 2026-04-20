const { createApp, ref, onMounted } = Vue;

createApp({
    setup() {
        // 这里可以尝试从 localStorage 获取登录时存入的用户名
        const username = ref(localStorage.getItem('currentUser') || '用户');

        const goTo = (path) => {
            window.location.href = path;
        };

        const logout = () => {
            localStorage.removeItem('isLoggedIn');
            window.location.href = 'login.html';
        };

        return { username, goTo, logout };
    }
}).mount('#app');