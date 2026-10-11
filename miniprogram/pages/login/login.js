// pages/login/login.js
const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    agreed: false,
    loggingIn: false
  },

  onShow() {
    // 已登录则直接进入首页
    const token = wx.getStorageSync('token');
    if (token) {
      wx.reLaunch({ url: '/pages/home/index' });
    }
  },

  toggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  openAgreement() {
    wx.navigateTo({ url: '/pages/agreement/notice' });
  },

  async handleLogin() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意免责声明与隐私政策', icon: 'none' });
      return;
    }
    if (this.data.loggingIn) return;
    this.setData({ loggingIn: true });
    try {
      // 1. wx.login 获取临时 code
      const loginRes = await new Promise((resolve, reject) => {
        wx.login({ success: resolve, fail: reject });
      });
      const code = loginRes.code || 'devtools-mock-code';

      // 2. 演示环境：后端开启 mock-login 时接受固定 phoneCode；
      //    正式环境需改为 <button open-type="getPhoneNumber"> 拿到真实手机号授权 code。
      const phoneCode = '__DEVTOOLS_MOCK_PHONE__';

      const result = await request({
        url: '/api/wx/login',
        method: 'POST',
        data: { code, phoneCode },
        showLoading: true
      });
      const payload = result.data || {};
      wx.setStorageSync('token', payload.token || '');
      wx.setStorageSync('userInfo', payload.userInfo || {});
      app.globalData.token = payload.token || '';
      app.globalData.userInfo = payload.userInfo || {};
      wx.reLaunch({ url: '/pages/home/index' });
    } catch (err) {
      console.error('登录失败', err);
    } finally {
      this.setData({ loggingIn: false });
    }
  }
});
