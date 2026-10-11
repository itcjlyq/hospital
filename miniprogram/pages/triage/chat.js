// pages/triage/chat.js
const api = require('../../utils/api');

Page({
  data: {
    sessionId: null,
    messages: [],
    inputValue: '',
    status: 'ONGOING',
    riskLevel: null,
    sending: false,
    scrollToView: ''
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
      this.setData({
        messages: (d.messages || []).map((m) => ({
          role: m.role,
          content: m.content,
          options: m.options || []
        })),
        status: d.status,
        riskLevel: d.assessment ? d.assessment.riskLevel : null
      }, () => this.scrollToEnd());
    } catch (e) {
      console.error(e);
    }
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value });
  },

  onTapOption(e) {
    const text = e.currentTarget.dataset.text;
    this.send(text);
  },

  onSend() {
    const text = (this.data.inputValue || '').trim();
    if (!text || this.data.sending || this.data.status !== 'ONGOING') return;
    this.send(text);
  },

  async send(content) {
    this.setData({
      sending: true,
      inputValue: '',
      messages: this.data.messages.concat([{ role: 'USER', content, options: [] }])
    }, () => this.scrollToEnd());

    try {
      const res = await api.triageSend(this.data.sessionId, content);
      const turn = res.data;
      const newMessages = this.data.messages.concat([{
        role: turn.message.role,
        content: turn.message.content,
        options: turn.message.options || []
      }]);
      this.setData({
        messages: newMessages,
        status: turn.status,
        riskLevel: turn.riskLevel
      }, () => this.scrollToEnd());

      if (turn.status === 'EMERGENCY') {
        wx.vibrateLong && wx.vibrateLong();
      }
    } catch (e) {
      console.error(e);
      this.setData({ inputValue: content });
    } finally {
      this.setData({ sending: false });
    }
  },

  scrollToEnd() {
    const last = this.data.messages[this.data.messages.length - 1];
    this.setData({ scrollToView: 'msg-bottom' });
  },

  viewReport() {
    wx.redirectTo({ url: `/pages/triage/report?id=${this.data.sessionId}` });
  },

  newTriage() {
    wx.redirectTo({ url: '/pages/triage/start' });
  }
});
