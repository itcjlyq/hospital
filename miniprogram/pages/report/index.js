// pages/report/index.js
const api = require('../../utils/api');

const TYPES = [
  { value: 'LIVER_FUNCTION', label: '肝功能' },
  { value: 'AFP', label: '甲胎蛋白 AFP' },
  { value: 'ULTRASOUND', label: '腹部超声' },
  { value: 'OTHER', label: '其他检查' }
];

// 预置指标带展示单位（仅 UI 提示，不参与提交与校验；提交仍为 名称→原始值 文本）
const DEFAULT_INDICATORS = [
  { name: 'ALT（谷丙转氨酶）', value: '', unit: 'U/L' },
  { name: 'AST（谷草转氨酶）', value: '', unit: 'U/L' },
  { name: '总胆红素', value: '', unit: 'μmol/L' },
  { name: '白蛋白', value: '', unit: 'g/L' },
  { name: 'AFP（甲胎蛋白）', value: '', unit: 'ng/mL' }
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
    submitting: false,
    focusedIdx: -1
  },

  onShow() {
    this.loadProfiles();
    this.loadHistory();
  },

  // 表单有未保存修改时，拦截返回（基础库支持时生效）
  markDirty() {
    this._dirty = true;
    if (wx.enableAlertBeforeUnload) {
      wx.enableAlertBeforeUnload({
        message: '检验数据尚未保存生成解读，确定离开吗？'
      });
    }
  },

  markSaved() {
    this._dirty = false;
    if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
  },

  onUnload() {
    this.markSaved();
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
    this.markDirty();
  },

  onMetricFocus(e) {
    this.setData({ focusedIdx: Number(e.currentTarget.dataset.idx) });
  },

  onMetricBlur() {
    this.setData({ focusedIdx: -1 });
  },

  onIndicatorInput(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ [`indicators[${idx}].value`]: e.detail.value });
    this.markDirty();
  },

  onIndicatorNameInput(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ [`indicators[${idx}].name`]: e.detail.value });
    this.markDirty();
  },

  addIndicator() {
    this.setData({ indicators: this.data.indicators.concat([{ name: '', value: '', custom: true }]) });
    this.markDirty();
  },

  removeIndicator(e) {
    const idx = e.currentTarget.dataset.idx;
    const indicators = this.data.indicators.slice();
    indicators.splice(idx, 1);
    this.setData({ indicators });
    this.markDirty();
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
      this.markSaved();
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
