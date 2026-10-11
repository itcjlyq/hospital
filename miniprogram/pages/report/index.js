// pages/report/index.js
const api = require('../../utils/api');

const TYPES = [
  { value: 'LIVER_FUNCTION', label: '肝功能' },
  { value: 'AFP', label: '甲胎蛋白 AFP' },
  { value: 'ULTRASOUND', label: '腹部超声' },
  { value: 'OTHER', label: '其他检查' }
];

const DEFAULT_INDICATORS = [
  { name: 'ALT（谷丙转氨酶）', value: '' },
  { name: 'AST（谷草转氨酶）', value: '' },
  { name: '总胆红素', value: '' },
  { name: '白蛋白', value: '' },
  { name: 'AFP（甲胎蛋白）', value: '' }
];

function today() {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

Page({
  data: {
    profiles: [],
    profileIndex: 0,
    types: TYPES.map((t) => t.label),
    typeIndex: 0,
    reportDate: today(),
    indicators: DEFAULT_INDICATORS.map((i) => ({ ...i })),
    interpretation: '',
    history: [],
    expandedId: null,
    submitting: false
  },

  onShow() {
    this.loadProfiles();
    this.loadHistory();
  },

  async loadProfiles() {
    try {
      const res = await api.profileList();
      this.setData({ profiles: res.data || [] });
    } catch (e) {
      console.error(e);
    }
  },

  async loadHistory() {
    try {
      const res = await api.reportList();
      const typeMap = Object.fromEntries(TYPES.map((t) => [t.value, t.label]));
      this.setData({
        history: (res.data || []).map((r) => ({ ...r, typeLabel: typeMap[r.reportType] || '其他检查' }))
      });
    } catch (e) {
      console.error(e);
    }
  },

  onProfileChange(e) {
    this.setData({ profileIndex: Number(e.detail.value) });
  },

  onTypeChange(e) {
    this.setData({ typeIndex: Number(e.detail.value) });
  },

  onDateChange(e) {
    this.setData({ reportDate: e.detail.value });
  },

  onIndicatorInput(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ [`indicators[${idx}].value`]: e.detail.value });
  },

  onIndicatorNameInput(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ [`indicators[${idx}].name`]: e.detail.value });
  },

  addIndicator() {
    this.setData({ indicators: this.data.indicators.concat([{ name: '', value: '', custom: true }]) });
  },

  removeIndicator(e) {
    const idx = e.currentTarget.dataset.idx;
    const indicators = this.data.indicators.slice();
    indicators.splice(idx, 1);
    this.setData({ indicators });
  },

  async onSubmit() {
    const { profiles, profileIndex, typeIndex, reportDate, indicators } = this.data;
    if (!profiles.length) {
      wx.showToast({ title: '请先建立健康档案', icon: 'none' });
      return;
    }
    const map = {};
    indicators.forEach((i) => {
      const name = (i.name || '').trim();
      const value = (i.value || '').trim();
      if (name && value) map[name] = value;
    });
    if (Object.keys(map).length === 0) {
      wx.showToast({ title: '请至少填写一项指标', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    try {
      const res = await api.reportInterpret({
        profileId: profiles[profileIndex].id,
        reportType: TYPES[typeIndex].value,
        reportDate,
        indicators: map
      });
      this.setData({ interpretation: res.data.aiInterpretation || '' });
      this.loadHistory();
      wx.pageScrollTo({ scrollTop: 0, duration: 200 });
    } catch (e) {
      console.error(e);
    } finally {
      this.setData({ submitting: false });
    }
  },

  toggleHistory(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ expandedId: this.data.expandedId === id ? null : id });
  }
});
