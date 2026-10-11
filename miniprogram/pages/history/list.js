// pages/history/list.js
const api = require('../../utils/api');

const STATUS_TEXT = {
  ONGOING: { text: '进行中', cls: 'ongoing' },
  FINISHED: { text: '已完成', cls: 'finished' },
  EMERGENCY: { text: '急症中断', cls: 'emergency' }
};

const RISK_TEXT = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  EMERGENCY: '急症'
};

Page({
  data: {
    sessions: []
  },

  onShow() {
    this.loadList();
  },

  async loadList() {
    try {
      const res = await api.triageList();
      const sessions = (res.data || []).map((s) => ({
        ...s,
        statusText: (STATUS_TEXT[s.status] || { text: s.status }).text,
        statusCls: (STATUS_TEXT[s.status] || {}).cls || '',
        riskText: s.riskLevel ? RISK_TEXT[s.riskLevel] : ''
      }));
      this.setData({ sessions });
    } catch (e) {
      console.error(e);
    }
  },

  openSession(e) {
    const { id, status } = e.currentTarget.dataset;
    const url = status === 'ONGOING'
      ? `/pages/triage/chat?id=${id}`
      : `/pages/triage/report?id=${id}`;
    wx.navigateTo({ url });
  }
});
