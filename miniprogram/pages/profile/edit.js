// pages/profile/edit.js
const api = require('../../utils/api');

const RELATIONS = ['本人', '父母', '子女', '配偶', '其他'];
const FATTY = ['无', '轻度', '中重度'];

Page({
  data: {
    id: null,
    relations: RELATIONS,
    relationIndex: 0,
    fattyOptions: FATTY,
    fattyIndex: 0,
    form: {
      realName: '',
      gender: '男',
      age: '',
      relation: '本人',
      hbvCarrier: 0,
      hcvCarrier: 0,
      cirrhosis: 0,
      fattyLiver: 0,
      diabetes: 0,
      alcoholHistory: '',
      smoking: 0,
      familyLiverCancer: 0,
      allergy: '',
      currentMedication: '',
      remark: ''
    },
    saving: false
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ id: Number(options.id) });
      wx.setNavigationBarTitle({ title: '编辑档案' });
      this.loadProfile(options.id);
    }
  },

  // 未保存修改时拦截返回（基础库支持时生效）
  markDirty() {
    this._dirty = true;
    if (wx.enableAlertBeforeUnload) {
      wx.enableAlertBeforeUnload({
        message: '档案修改尚未保存，确定离开吗？'
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

  async loadProfile(id) {
    try {
      const res = await api.profileDetail(id);
      const p = res.data || {};
      this.setData({
        relationIndex: Math.max(0, RELATIONS.indexOf(p.relation || '本人')),
        fattyIndex: p.fattyLiver || 0,
        form: {
          realName: p.realName || '',
          gender: p.gender || '男',
          age: p.age == null ? '' : String(p.age),
          relation: p.relation || '本人',
          hbvCarrier: p.hbvCarrier || 0,
          hcvCarrier: p.hcvCarrier || 0,
          cirrhosis: p.cirrhosis || 0,
          fattyLiver: p.fattyLiver || 0,
          diabetes: p.diabetes || 0,
          alcoholHistory: p.alcoholHistory || '',
          smoking: p.smoking || 0,
          familyLiverCancer: p.familyLiverCancer || 0,
          allergy: p.allergy || '',
          currentMedication: p.currentMedication || '',
          remark: p.remark || ''
        }
      });
    } catch (e) {
      console.error(e);
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
    this.markDirty();
  },

  chooseGender(e) {
    this.setData({ 'form.gender': e.currentTarget.dataset.gender });
    this.markDirty();
  },

  onRelationChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ relationIndex: idx, 'form.relation': RELATIONS[idx] });
    this.markDirty();
  },

  onFattyChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ fattyIndex: idx, 'form.fattyLiver': idx });
    this.markDirty();
  },

  onSwitch(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value ? 1 : 0 });
    this.markDirty();
  },

  async onSave() {
    const form = this.data.form;
    if (!form.realName.trim()) {
      wx.showToast({ title: '请填写姓名或称呼', icon: 'none' });
      return;
    }
    if (form.age !== '' && (Number(form.age) < 0 || Number(form.age) > 120)) {
      wx.showToast({ title: '年龄不合法', icon: 'none' });
      return;
    }
    const payload = { ...form, age: form.age === '' ? null : Number(form.age) };
    this.setData({ saving: true });
    try {
      if (this.data.id) {
        await api.profileUpdate(this.data.id, payload);
      } else {
        await api.profileCreate(payload);
      }
      wx.showToast({ title: '已保存', icon: 'success' });
      this.markSaved();
      setTimeout(() => wx.navigateBack(), 400);
    } catch (e) {
      console.error(e);
    } finally {
      this.setData({ saving: false });
    }
  }
});
