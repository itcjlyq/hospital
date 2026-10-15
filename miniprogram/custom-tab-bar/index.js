Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', icon: 'ic-home', iconOn: 'ic-home-on' },
      { pagePath: '/pages/history/list', text: '记录', icon: 'ic-record', iconOn: 'ic-record-on' },
      { pagePath: '/pages/center/index', text: '我的', icon: 'ic-mine', iconOn: 'ic-mine-on' }
    ]
  },

  pageLifetimes: {
    show() {
      this.refresh();
    }
  },

  methods: {
    refresh() {
      const pages = getCurrentPages();
      const route = pages.length ? pages[pages.length - 1].route : '';
      const map = {
        'pages/home/index': 0,
        'pages/history/list': 1,
        'pages/center/index': 2
      };
      this.setData({ selected: map[route] === undefined ? 0 : map[route] });
    },

    switchTab(e) {
      const url = e.currentTarget.dataset.path;
      if (!url) return;
      wx.switchTab({ url });
    }
  }
});
