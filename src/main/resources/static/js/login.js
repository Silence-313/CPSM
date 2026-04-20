const { createApp, ref, computed } = Vue;

createApp({
    setup() {
        const view = ref('login');
        const form = ref({
            account: '',
            username: '',
            password: '',
            phone: '',
            email: ''
        });
        const msg = ref({ content: '', type: '' });

        const title = computed(() => {
            const titles = { login: '欢迎回来', reg: '加入监测', forget: '重置密码' };
            return titles[view.value];
        });

        const subtitle = computed(() => {
            return view.value === 'login' ? '请登录您的账号' : '开始追踪微博实时热度';
        });

        const submitText = computed(() => {
            const texts = { login: '立即登录', reg: '提交注册', forget: '确认修改' };
            return texts[view.value];
        });

        const showMsg = (content, type = 'error') => {
            msg.value = { content, type };
            setTimeout(() => msg.value.content = '', 4000);
        };

        const switchView = (v) => {
            view.value = v;
            msg.value.content = '';
            // 切换时清空表单，防止干扰
            form.value = { account: '', username: '', password: '', phone: '', email: '' };
        };

        const handleSubmit = async () => {
            // 1. 基础校验
            if (view.value === 'login' && (!form.value.account || !form.value.password)) {
                showMsg('请填写账号和密码'); return;
            }
            if (view.value === 'reg' && (!form.value.username || !form.value.password || (!form.value.phone && !form.value.email))) {
                showMsg('请填写用户名、密码及至少一种联系方式'); return;
            }

            let url = `/api/user/${view.value}`;
            // 根据不同视图构建发送给后端的 Payload
            let bodyData = {};

            if (view.value === 'reg') {
                bodyData = {
                    username: form.value.username,
                    password: form.value.password,
                    phone: form.value.phone,
                    email: form.value.email
                };
            } else if (view.value === 'forget') {
                bodyData = {
                    username: form.value.username,
                    account: form.value.account,
                    password: form.value.password
                };
            } else {
                bodyData = {
                    account: form.value.account,
                    password: form.value.password
                };
            }

            try {
                const res = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(bodyData)
                });

                const data = await res.text();

                if (res.ok) {
                    showMsg('登录成功！跳转中...', 'success');
                    // 💡 建议在跳转前存一下用户名，方便 Portal 页显示
                    localStorage.setItem('currentUser', form.value.account);
                    localStorage.setItem('isLoggedIn', 'true');
                    setTimeout(() => {
                        window.location.href = 'portal.html';
                    }, 1000);
                } else {
                    showMsg(data || '操作失败，请核对信息');
                }
            } catch (e) {
                showMsg('网络异常，无法连接服务器');
            }
        };

        return { view, form, msg, title, subtitle, submitText, switchView, handleSubmit };
    }
}).mount('#app');