// pages/triage/start.js
const api = require('../../utils/api');

Page({
  data: {
    profiles: [],
    selectedId: null,
    complaint: '',
    submitting: false
  },

  onShow() {
    this.loadProfiles();
  },

  async loadProfiles() {
    try {
      const res = await api.profileList();
      const profiles = res.data || [];
      const selectedId = this.data.selectedId || (profiles.length ? profiles[0].id : null);
      this.setData({ profiles, selectedId });
    } catch (e) {
      console.error(e);
    }
  },

  chooseProfile(e) {
    this.setData({ selectedId: e.currentTarget.dataset.id });
  },

  onComplaintInput(e) {
    this.setData({ complaint: e.detail.value });
  },

  goCreateProfile() {
    wx.navigateTo({ url: '/pages/profile/edit' });
  },

  async onSubmit() {
    const { selectedId, complaint } = this.data;
    if (!selectedId) {
      wx.showToast({ title: '请先选择或新建档案', icon: 'none' });
      return;
    }
    if (!complaint.trim()) {
      wx.showToast({ title: '请描述你的主要不适', icon: 'none' });
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    try {
      const res = await api.triageCreate(selectedId, complaint.trim());
      const turn = res.data;
      // 急症在首轮即命中：直接跳报告页展示急诊提示
      const url = turn.status === 'EMERGENCY'
        ? `/pages/triage/report?id=${turn.sessionId}&emergency=1`
        : `/pages/triage/chat?id=${turn.sessionId}`;
      wx.redirectTo({ url });
    } catch (e) {
      console.error(e);
    } finally {
      this.setData({ submitting: false });
    }
  }
});
