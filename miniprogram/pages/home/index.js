// pages/home/index.js
const api = require('../../utils/api');
const app = getApp();

const REPORT_TYPE_LABEL = {
  LIVER_FUNCTION: '肝功能检查',
  AFP: '甲胎蛋白 AFP',
  ULTRASOUND: '腹部超声',
  OTHER: '其他检查'
};

// 核心指标提取规则（仅展示，不做任何异常判断）
const CORE_METRICS = [
  { key: 'ALT', name: '谷丙转氨酶', match: /(^ALT|谷丙)/i },
  { key: 'AST', name: '谷草转氨酶', match: /(^AST|谷草)/i },
  { key: 'GGT', name: '谷氨酰转移酶', match: /(^GGT|谷氨酰)/i }
];

function buildTodayText() {
  const d = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · 星期${week}`;
}

Page({
  data: {
    userInfo: null,
    profileCount: 0,
    notices: [],
    todayText: '',
    loading: true,
    loadError: false,
    latestReport: null
  },

  onLoad() {
    this.setData({ todayText: buildTodayText() });
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
    this.setData({ loading: true, loadError: false });
    // 三个请求互不阻塞，单个失败不影响其他区块
    const results = await Promise.all([
      api.profileList().then((r) => r).catch(() => null),
      api.noticeActive().then((r) => r).catch(() => null),
      api.reportList().then((r) => r).catch(() => null)
    ]);
    const [profiles, noticeRes, reportRes] = results;
    const allFailed = results.every((r) => r === null);

    const patch = { loading: false, loadError: allFailed };
    if (profiles) patch.profileCount = (profiles.data || []).length;
    if (noticeRes) patch.notices = noticeRes.data || [];
    if (reportRes) patch.latestReport = this.buildLatestReport(reportRes.data || []);
    this.setData(patch);

    if (allFailed) console.error('首页数据全部加载失败');
  },

  // 从历史检验记录中取最近一条，并提取 ALT/AST/GGT（纯展示）
  buildLatestReport(records) {
    if (!records.length) return null;
    const sorted = records.slice().sort((a, b) => String(b.reportDate).localeCompare(String(a.reportDate)));
    const latest = sorted[0];
    const indicators = latest.indicators || {};
    const metrics = [];
    CORE_METRICS.forEach((def) => {
      Object.keys(indicators).forEach((rawName) => {
        if (def.match.test(rawName)) {
          metrics.push({ key: def.key, name: def.name, value: indicators[rawName] });
        }
      });
    });
    return {
      id: latest.id,
      reportDate: latest.reportDate,
      typeLabel: REPORT_TYPE_LABEL[latest.reportType] || '检查记录',
      metrics: metrics.slice(0, 3)
    };
  },

  goCenter() {
    wx.switchTab({ url: '/pages/center/index' });
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
