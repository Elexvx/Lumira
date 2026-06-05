import app from './zh-CN/app';
import common from './zh-CN/common';
import nav from './zh-CN/nav';
import pageLocalization from './zh-CN/pageLocalization';
import pageProfile from './zh-CN/pageProfile';
import pageSecurity from './zh-CN/pageSecurity';
import pagePlugins from './zh-CN/pagePlugins';
import pageLogin from './zh-CN/pageLogin';
import chrome from './zh-CN/chrome';
import messageCenter from './zh-CN/messageCenter';
import systemFiles from './zh-CN/systemFiles';

export default {
  ...app,
  ...common,
  ...nav,
  ...pageLocalization,
  ...pageProfile,
  ...pageSecurity,
  ...pagePlugins,
  ...pageLogin,
  ...chrome,
  ...messageCenter,
  ...systemFiles,
};
