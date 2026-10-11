// pages/triage/report.js
const api = require('../../utils/api');

const LEVEL_META = {
  LOW: { text: '低风险 / 日常关注', cls: 'low', color: '#2e8b7d' },
  MEDIUM: { text: '建议关注 / 门诊评估', cls: 'medium', color: '#c98a1e' },
  HIGH: { text: '建议尽快线下就医', cls: 'high', color: '#d9534f' },
  EMERGENCY: { text: '急症风险 / 立即就医', cls: 'emergency', color: '#c83c31' }
};

Page({
  data: {
    sessionId: null,
    detail: null,
    level: null,
    messages: []
  },

  onLoad(options) {
    if (!options.id) {
      wx.navigateBack();
      return;
    }
    this.setData({ sessionId: Number(options.id) });
    this.loadDetail();
  },

  async loadDetail() {
    try {
      const res = await api.triageDetail(this.data.sessionId);
      const d = res.data;
      const level = LEVEL_META[d.assessment ? d.assessment.riskLevel : d.status] || LEVEL_META.MEDIUM;
      this.setData({ detail: d, level, messages: d.messages || [] });
    } catch (e) {
      console.error(e);
    }
  },

  callEmergency() {
    wx.makePhoneCall({ phoneNumber: '120' });
  },

  newTriage() {
    wx.redirectTo({ url: '/pages/triage/start' });
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/index' });
  }
});
