import app from './en-US/app';
import common from './en-US/common';
import nav from './en-US/nav';
import pageLocalization from './en-US/pageLocalization';
import pageProfile from './en-US/pageProfile';
import pageSecurity from './en-US/pageSecurity';
import pagePlugins from './en-US/pagePlugins';
import pageLogin from './en-US/pageLogin';
import chrome from './en-US/chrome';
import messageCenter from './en-US/messageCenter';
import systemFiles from './en-US/systemFiles';

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
