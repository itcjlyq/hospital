// pages/center/index.js
const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    userInfo: {}
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({ userInfo: wx.getStorageSync('userInfo') || {} });
    api.userMe().then((res) => {
      this.setData({ userInfo: res.data || {} });
      wx.setStorageSync('userInfo', res.data || {});
      app.globalData.userInfo = res.data || {};
    }).catch(() => {});
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/list' });
  },

  goReport() {
    wx.navigateTo({ url: '/pages/report/index' });
  },

  goNotice() {
    wx.navigateTo({ url: '/pages/agreement/notice' });
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      success: (res) => {
        if (!res.confirm) return;
        wx.removeStorageSync('token');
        wx.removeStorageSync('userInfo');
        app.globalData.token = null;
        app.globalData.userInfo = null;
        wx.reLaunch({ url: '/pages/login/login' });
      }
    });
  }
});
