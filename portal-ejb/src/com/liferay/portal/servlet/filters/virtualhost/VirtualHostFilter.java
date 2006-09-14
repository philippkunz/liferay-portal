/**
 * Copyright (c) 2000-2006 Liferay, LLC. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.servlet.filters.virtualhost;

import com.liferay.portal.LayoutFriendlyURLException;
import com.liferay.portal.NoSuchLayoutException;
import com.liferay.portal.NoSuchLayoutSetException;
import com.liferay.portal.model.LayoutSet;
import com.liferay.portal.service.spring.LayoutSetLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.WebKeys;
import com.liferay.util.GetterUtil;
import com.liferay.util.SystemProperties;
import com.liferay.util.Validator;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <a href="VirtualHostFilter.java.html"><b><i>View Source</i></b></a>
 *
 * @author  Joel Kozikowski
 * @author  Brian Wing Shun Chan
 *
 */
public class VirtualHostFilter implements Filter {

	public static final boolean USE_VIRTUAL_HOST_FILTER = GetterUtil.getBoolean(
		SystemProperties.get(VirtualHostFilter.class.getName()), true);

	public void init(FilterConfig config) throws ServletException {
		_ctx = config.getServletContext();

		_companyId = _ctx.getInitParameter("company_id");
	}

	public void doFilter(
			ServletRequest req, ServletResponse res, FilterChain chain)
		throws IOException, ServletException {

		if (_log.isDebugEnabled()) {
			if (USE_VIRTUAL_HOST_FILTER) {
				_log.debug("Virtual host is enabled");
			}
			else {
				_log.debug("Virtual host is disabled");
			}
		}

		HttpServletRequest httpReq = (HttpServletRequest)req;
		HttpServletResponse httpRes = (HttpServletResponse)res;

		if (_log.isDebugEnabled()) {
			_log.debug("Received " + httpReq.getRequestURL());
		}

		String friendlyURL = httpReq.getRequestURI().toLowerCase();

		String redirect = null;

		if (USE_VIRTUAL_HOST_FILTER && _isValidFriendlyURL(friendlyURL)) {
			String host = PortalUtil.getHost(httpReq);
			String servletPath = httpReq.getServletPath();
			String mainPath = (String)_ctx.getAttribute(WebKeys.MAIN_PATH);
			String friendlyURLPublicPath = (String)_ctx.getAttribute(
				WebKeys.FRIENDLY_URL_PUBLIC_PATH);
			String friendlyURLPrivatePath = (String)_ctx.getAttribute(
				WebKeys.FRIENDLY_URL_PRIVATE_PATH);

			if (Validator.isNotNull(host) && Validator.isNotNull(servletPath) &&
				!servletPath.startsWith(mainPath) &&
				!servletPath.startsWith(friendlyURLPublicPath) &&
				!servletPath.startsWith(friendlyURLPrivatePath)) {

			   try {
					LayoutSet layoutSet =
						LayoutSetLocalServiceUtil.getLayoutSet(
							_companyId, host);

					String ownerId = layoutSet.getOwnerId();

					redirect = PortalUtil.getLayoutActualURL(
						ownerId, mainPath, friendlyURL);
				}
				catch (NoSuchLayoutException nsle) {
					_log.warn(nsle);
				}
				catch (NoSuchLayoutSetException nslse) {
					_log.warn(nslse);
				}
				catch (Exception e) {
					_log.error("Could not look up virtual host " + host, e);
				}
			}
		}

		if (redirect != null) {
			if (_log.isDebugEnabled()) {
				_log.debug("Redirect to " + redirect);
			}

			RequestDispatcher rd = _ctx.getRequestDispatcher(redirect);

			rd.forward(req, res);
		}
		else {
			chain.doFilter(req, res);
		}
	}

	public void destroy() {
	}

	private boolean _isValidFriendlyURL(String friendlyURL) {
		String[] keywords = PropsUtil.getArray(
			PropsUtil.LAYOUT_FRIENDLY_URL_KEYWORDS);

		for (int i = 0; i < keywords.length; i++) {
			if ((LayoutFriendlyURLException.validate(friendlyURL) > -1) ||
				(friendlyURL.indexOf(keywords[i]) != -1)) {

				return false;
			}
		}

		return true;
	}

	private static Log _log = LogFactory.getLog(VirtualHostFilter.class);

	private ServletContext _ctx;
	private String _companyId;

}