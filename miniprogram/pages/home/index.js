// pages/home/index.js
const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    userInfo: null,
    profileCount: 0,
    notices: []
  },

  onShow() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({ userInfo: wx.getStorageSync('userInfo') || {} });
    this.loadData();
  },

  async loadData() {
    try {
      const [profiles, noticeRes] = await Promise.all([
        api.profileList(),
        api.noticeActive()
      ]);
      this.setData({
        profileCount: (profiles.data || []).length,
        notices: noticeRes.data || []
      });
    } catch (e) {
      console.error('首页数据加载失败', e);
    }
  },

  goTriage() {
    if (this.data.profileCount === 0) {
      wx.showModal({
        title: '请先建立健康档案',
        content: '初筛需要结合就诊人的年龄、既往肝病史等信息，先建立一份健康档案吧。',
        confirmText: '去建档',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) wx.navigateTo({ url: '/pages/profile/edit' });
        }
      });
      return;
    }
    wx.navigateTo({ url: '/pages/triage/start' });
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/list' });
  },

  goReport() {
    if (this.data.profileCount === 0) {
      wx.showToast({ title: '请先建立健康档案', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages/report/index' });
  },

  goHistory() {
    wx.switchTab({ url: '/pages/history/list' });
  }
});
