package top.ysqorz.forum.controller.front;


import org.apache.shiro.authz.annotation.RequiresRoles;
import org.apache.shiro.crypto.hash.Md5Hash;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.ysqorz.forum.common.ResultModel;
import top.ysqorz.forum.common.StatusCode;
import top.ysqorz.forum.dto.LoginDTO;
import top.ysqorz.forum.dto.RegisterDTO;
import top.ysqorz.forum.dto.UserLoginInfo;
import top.ysqorz.forum.oauth.BaiduProvider;
import top.ysqorz.forum.oauth.GiteeProvider;
import top.ysqorz.forum.oauth.QQProvider;
import top.ysqorz.forum.po.User;
import top.ysqorz.forum.service.RedisService;
import top.ysqorz.forum.service.UserService;
import top.ysqorz.forum.shiro.ShiroUtils;
import top.ysqorz.forum.utils.RandomUtils;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 通过注解实现权限控制
 * 在方法前添加注解 @RequiredRoles 或 @RequiredPermissions
 *
 * @author passerbyYSQ
 * @create 2020-08-20 23:54
 */
@Controller
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private RedisService redisService;

    @Resource
    private GiteeProvider giteeProvider;

    @Resource
    private QQProvider qqProvider;

	@Resource
    private BaiduProvider baiduProvider;

    @RequiresRoles({"ysq"})
    @RequestMapping("/info")
    @ResponseBody
    public ResultModel<String> index() {
        return ResultModel.success("用户信息！这个接口需要携带有效的token才能访问");
    }

    /**
     * 用户注册
     */
    @PostMapping("/reg")
    @ResponseBody
    public ResultModel register(@Validated RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getRePassword())) {
            return ResultModel.failed(StatusCode.PASSWORD_NOT_EQUAL); // 两次输入密码不一致
        }
        String correctCaptcha = redisService.getCaptcha(dto.getToken());
        if (ObjectUtils.isEmpty(correctCaptcha)) {
            return ResultModel.failed(StatusCode.CAPTCHA_EXPIRED); // 验证码过期
        }
        if (!dto.getCaptcha().equalsIgnoreCase(correctCaptcha)) {
            return ResultModel.failed(StatusCode.CAPTCHA_INVALID); // 验证码错误
        }

        User user = userService.getUserByEmail(dto.getEmail());
        if (!ObjectUtils.isEmpty(user)) {
            return ResultModel.failed(StatusCode.EMAIL_ISEXIST); // 该邮箱已注册
        }

        userService.register(dto);

        return ResultModel.success();
    }

    /**
     * 跳转到注册页面
     */
    @GetMapping("/reg")
    public String registerPage(Model model) {
        // 用于验证码缓存和校验。植入到注册的登录页面的隐藏表单元素中
        String token = RandomUtils.generateUUID();
        model.addAttribute("token", token);
        return "front/user/reg";
    }

    /**
     * 跳转到登录页面
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        // 用于验证码缓存和校验。植入到页面的登录页面的隐藏表单元素中
        String token = RandomUtils.generateUUID();
        model.addAttribute("token", token);
        return "front/user/login";
    }

    /**
     * 用户登录的API
     */
    @PostMapping("/login")
    @ResponseBody
    public ResultModel<UserLoginInfo> login(@Validated LoginDTO dto, HttpServletResponse response) {
        String correctCaptcha = redisService.getCaptcha(dto.getToken());
        if (ObjectUtils.isEmpty(correctCaptcha)) {
            return ResultModel.failed(StatusCode.CAPTCHA_EXPIRED); // 验证码过期
        }
        if (!dto.getCaptcha().equalsIgnoreCase(correctCaptcha)) {
            return ResultModel.failed(StatusCode.CAPTCHA_INVALID); // 验证码过期
        }

        User user = userService.getUserByEmail(dto.getEmail());
        if (ObjectUtils.isEmpty(user)) {
            return ResultModel.failed(StatusCode.EMAIL_INCORRECT); // 邮箱错误
        }

        // 以注册时的相同规则，加密用户输入的密码
        Md5Hash md5Hash = new Md5Hash(dto.getPassword(), user.getLoginSalt(), 1024);
        if (!user.getPassword().equals(md5Hash.toHex())) {
            return ResultModel.failed(StatusCode.PASSWORD_INCORRECT); // 密码错误
        }

        userService.clearShiroAuthCache(user);
        String token = userService.login(user.getId(), response);

        // 为什么登录不使用UsernamePasswordToken和定义专门的LoginRealm（Service层的逻辑）来处理UsernamePasswordToken？
        // 由于密码登录只用一次，成功之后都凭借jwt令牌来访问
        // 经过LoginRealm后所缓存起来的认证信息之后多不会被用到。。个人想法

        // 将token放到请求头中，方便前端判断有无token刷新
        // 将常显的数据返回给前端缓存
        return ResultModel.success(new UserLoginInfo(token, user));
    }

    /**
     * 销毁主体的认证信息
     */
    @RequestMapping("/logout")
    public String logout(HttpServletResponse response) {
        userService.logout();
        Cookie cookie = new Cookie("token", "");
        cookie.setMaxAge(0);
        cookie.setPath("/"); // ！！！
        response.addCookie(cookie);
        return "redirect:/";
    }

    @GetMapping("/oauth/gitee/authorize")
    public void giteeAuthorize(@RequestHeader(defaultValue = "") String referer,
                               HttpServletResponse response) throws IOException {
        giteeProvider.redirectAuthorize(referer, response);
    }

    /**
     * gitee第三方授权回调地址
     */
    @GetMapping("/oauth/gitee/callback")
    public String giteeCallback(@RequestParam(defaultValue = "") String state,
                                String code, HttpServletResponse response) throws IOException {
        // 校验state，防止CSRF
        String referer = giteeProvider.checkState(state);
        // 检查是否存在现有账号与第三方的账号已绑定
        User user = userService.oauth2Gitee(code);
        //检查已登录账号和获取的账号是否一致
        String msg = checkUser(user.getId());
        //不为空则证明绑定事务有错误信息，若还为指定页面的错误信息则直接返回
        if (!ObjectUtils.isEmpty(msg) && referer.indexOf("user/setting") > -1){
            int check = referer.indexOf("?");
            if(check > 0) {
                referer = referer.substring(0, check);
            }
            return "redirect:" + referer + "?msg=" + msg;
        }
        // 清除shiro的认证缓存，实现单点登录
        userService.clearShiroAuthCache(user);
        // 签发我们自己的token
        userService.login(user.getId(), response);
        // 重定向携带token
        //redirectAttributes.addAttribute("token", token);
        return "redirect:" + referer; // 不要加 "/"
    }

    @GetMapping("/oauth/qq/authorize")
    public void qqAuthorize(@RequestHeader(defaultValue = "") String referer,
                            HttpServletResponse response) throws IOException {
        qqProvider.redirectAuthorize(referer, response);
    }

    /**
     * qq第三方授权回调地址
     */
    @GetMapping("/oauth/qq/callback")
    public String qqCallback(@RequestParam(defaultValue = "") String state,
                                String code, HttpServletResponse response) throws IOException {
        String referer = qqProvider.checkState(state);
        User user = userService.oauth2QQ(code);
        String msg = checkUser(user.getId());
        if (!ObjectUtils.isEmpty(msg) && referer.indexOf("user/setting") > -1){
            int check = referer.indexOf("?");
            if(check > 0) {
                referer = referer.substring(0, check);
            }
            return "redirect:" + referer + "?msg=" + msg;
        }
        userService.clearShiroAuthCache(user);
        userService.login(user.getId(), response);
        //redirectAttributes.addAttribute("token", token);
        return "redirect:" + referer; // 不要加 "/"
    }

	@GetMapping("/oauth/baidu/authorize")
    public void baiduAuthorize(@RequestHeader(defaultValue = "") String referer,
                               HttpServletResponse response) throws IOException {
        baiduProvider.redirectAuthorize(referer, response);
    }


    /**
     * baidu第三方授权回调地址
     */
    @GetMapping("/oauth/baidu/callback")
    public String baiduCallback(@RequestParam(defaultValue = "") String state,
                                String code, HttpServletResponse response) throws IOException{
        String referer = baiduProvider.checkState(state);
        User user = userService.oauth2Baidu(code);
        String msg = checkUser(user.getId());
        if (!ObjectUtils.isEmpty(msg) && referer.indexOf("user/setting") > -1){
            int check = referer.indexOf("?");
            if(check > 0) {
                referer = referer.substring(0, check);
            }
            return "redirect:" + referer + "?msg=" + msg;
        }
        userService.clearShiroAuthCache(user);
        userService.login(user.getId(), response);
        return "redirect:" + referer; // 不要加 "/"
    }

    /**
     * 检查是否是由第三方账号获取的User
     * 且是否和当前User一致和是否登录
     */
    public String checkUser(int id) {
        if (ShiroUtils.getUserId() == null) {
            return "6104";
        }
        if (id != ShiroUtils.getUserId()) {
            return "6206";
        }
        return "2001";
    }

}
