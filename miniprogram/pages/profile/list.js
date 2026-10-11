// pages/profile/list.js
const api = require('../../utils/api');

Page({
  data: {
    profiles: []
  },

  onShow() {
    this.loadProfiles();
  },

  async loadProfiles() {
    try {
      const res = await api.profileList();
      const profiles = (res.data || []).map((p) => ({
        ...p,
        tags: this.buildTags(p)
      }));
      this.setData({ profiles });
    } catch (e) {
      console.error(e);
    }
  },

  buildTags(p) {
    const tags = [];
    if (p.hbvCarrier === 1) tags.push('乙肝');
    if (p.hcvCarrier === 1) tags.push('丙肝');
    if (p.cirrhosis === 1) tags.push('肝硬化');
    if (p.fattyLiver === 1) tags.push('脂肪肝(轻)');
    if (p.fattyLiver === 2) tags.push('脂肪肝(中重)');
    if (p.diabetes === 1) tags.push('糖尿病');
    if (p.familyLiverCancer === 1) tags.push('肝癌家族史');
    return tags;
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/profile/edit' });
  },

  goEdit(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/profile/edit?id=${id}` });
  },

  onDelete(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除档案',
      content: '删除后不可恢复，确定删除这份档案吗？（历史初筛记录会保留）',
      confirmColor: '#d9534f',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.profileDelete(id);
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadProfiles();
        } catch (err) {
          console.error(err);
        }
      }
    });
  }
});
