// utils/api.js — 业务接口封装
const { request } = require('./request');

// ---- 健康档案 ----
const profileList = () => request({ url: '/api/profile', method: 'GET', showLoading: false });
const profileDetail = (id) => request({ url: `/api/profile/${id}`, method: 'GET', showLoading: false });
const profileCreate = (data) => request({ url: '/api/profile', method: 'POST', data });
const profileUpdate = (id, data) => request({ url: `/api/profile/${id}`, method: 'PUT', data });
const profileDelete = (id) => request({ url: `/api/profile/${id}`, method: 'DELETE' });

// ---- AI 初筛 ----
const triageCreate = (profileId, chiefComplaint) =>
  request({ url: '/api/triage/sessions', method: 'POST', data: { profileId, chiefComplaint } });
const triageSend = (sessionId, content) =>
  request({ url: `/api/triage/sessions/${sessionId}/messages`, method: 'POST', data: { content } });
const triageList = () => request({ url: '/api/triage/sessions', method: 'GET', showLoading: false });
const triageDetail = (sessionId) =>
  request({ url: `/api/triage/sessions/${sessionId}`, method: 'GET', showLoading: false });

// ---- 检查指标 ----
const reportInterpret = (data) => request({ url: '/api/report/interpret', method: 'POST', data });
const reportList = (profileId) =>
  request({ url: '/api/report' + (profileId ? `?profileId=${profileId}` : ''), method: 'GET', showLoading: false });

// ---- 公告与用户 ----
const noticeActive = () => request({ url: '/api/notice/active', method: 'GET', showLoading: false });
const userMe = () => request({ url: '/api/user/me', method: 'GET', showLoading: false });

module.exports = {
  profileList,
  profileDetail,
  profileCreate,
  profileUpdate,
  profileDelete,
  triageCreate,
  triageSend,
  triageList,
  triageDetail,
  reportInterpret,
  reportList,
  noticeActive,
  userMe
};
