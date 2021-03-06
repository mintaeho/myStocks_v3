package com.nuritech.stock.mystock.common.interceptor;

import com.nuritech.stock.mystock.common.constant.CommonConstant;
import com.nuritech.stock.mystock.common.exception.ApiException;
import com.nuritech.stock.mystock.common.exception.ExceptionCode;
import com.nuritech.stock.mystock.common.util.JwtUtil;
import com.nuritech.stock.mystock.user.dto.SignInResponseDto;
import com.nuritech.stock.mystock.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class TokenAuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService detailsService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String accessToken = request.getHeader(CommonConstant.ACCESS_TOKEN);
        String refreshToken = request.getHeader(CommonConstant.REFRESH_TOKEN);
        String userId = request.getHeader(CommonConstant.USER_ID);

        log.info("TokenRefreshInterceptor::accessToken={}", accessToken);
        log.info("TokenRefreshInterceptor::refreshToken={}", refreshToken);
        log.info("TokenRefreshInterceptor::userId={}", userId);

        try {
            // refreshToken??? ???????????? refresh?????? ?????? ?????? ??????, refreshToken??? null?????? accessToken??????
            // response??? ???????????? ??????
            if (StringUtils.isNotEmpty(refreshToken)) {
                // token refresh ??????
                SignInResponseDto dto = userService.reIssueToken(Long.parseLong(userId), refreshToken);

                //response??? ???????????? ?????? ?????? ??????
                response.setHeader(CommonConstant.ACCESS_TOKEN, dto.getAccessToken());
                response.setHeader(CommonConstant.REFRESH_TOKEN, dto.getRefreshToken());

                log.info("TokenRefreshInterceptor::new accessToken={}", dto.getAccessToken());
                log.info("TokenRefreshInterceptor::new refreshToken={}", dto.getRefreshToken());
            }
            // access token ??????
            else {
                validateTokenExpired(accessToken);
                doAuthenticate(accessToken);
            }
        } catch (SecurityException | MalformedJwtException | DecodingException | SignatureException e) {
            throw new ApiException(ExceptionCode.WRONG_TYPE_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new ApiException(ExceptionCode.EXPIRED_TOKEN);
        } catch (UnsupportedJwtException e) {
            throw new ApiException(ExceptionCode.UNSUPPORTED_TOKEN);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ExceptionCode.WRONG_TYPE_TOKEN);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("================================================");
            log.error("ExceptionHandlerFilter - doFilterInternal() ????????????");
            log.error("Exception Message : {}", e.getMessage());
            log.error("Exception ExceptionCode : {}", request.getAttribute("exception"));
            log.error("Exception StackTrace : {");
            e.printStackTrace();
            log.error("}");
            log.error("================================================");
            throw new ApiException(ExceptionCode.UNKNOWN_ERROR);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex)
            throws Exception {
        // TODO Auto-generated method stub
        log.info("afterCompletion::new accessToken={}", response.getHeader(CommonConstant.ACCESS_TOKEN));
    }

    /**
     * token ???????????? ??????
     *
     * @param token
     */
    private void validateTokenExpired(String token) {
        // token ???????????? ??????
        if (jwtUtil.isTokenExpired(token))
            throw new ApiException(ExceptionCode.EXPIRED_TOKEN);
    }

    /**
     * toekn ????????? ????????? ?????? ??? ?????? ??????
     *
     * @param accessToken
     */
    private void doAuthenticate(String accessToken) {
        String userAuthId = jwtUtil.getClaimFromToken(accessToken, Claims::getAudience);
        UserDetails userDetails = detailsService.loadUserByUsername(userAuthId);
        makeAuthenticated(new UsernamePasswordAuthenticationToken(userDetails, accessToken, userDetails.getAuthorities()));
    }

    /**
     * ????????? ??????????????? ?????????????????? ??????
     *
     * @param authentication
     */
    private void makeAuthenticated(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

}
