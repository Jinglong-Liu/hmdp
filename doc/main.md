# 项目进展

# 初始版本开发
## v0.0.1
导入初始版本，导入SQL，修改配置，运行起来 (使用jdk11)

测试接口
```
/shop-type/list
```

```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "name": "美食",
            "icon": "/types/ms.png",
            "sort": 1
        },
        {
            "id": 2,
            "name": "KTV",
            "icon": "/types/KTV.png",
            "sort": 2
        },
        {
            "id": 3,
            "name": "丽人·美发",
            "icon": "/types/lrmf.png",
            "sort": 3
        },
        {
            "id": 10,
            "name": "美睫·美甲",
            "icon": "/types/mjmj.png",
            "sort": 4
        },
        {
            "id": 5,
            "name": "按摩·足疗",
            "icon": "/types/amzl.png",
            "sort": 5
        },
        {
            "id": 6,
            "name": "美容SPA",
            "icon": "/types/spa.png",
            "sort": 6
        },
        {
            "id": 7,
            "name": "亲子游乐",
            "icon": "/types/qzyl.png",
            "sort": 7
        },
        {
            "id": 8,
            "name": "酒吧",
            "icon": "/types/jiuba.png",
            "sort": 8
        },
        {
            "id": 9,
            "name": "轰趴馆",
            "icon": "/types/hpg.png",
            "sort": 9
        },
        {
            "id": 4,
            "name": "健身运动",
            "icon": "/types/jsyd.png",
            "sort": 10
        }
    ]
}
```

commit: 04cfa023197b0be633d67c9eac5579a7ab08c30f

可以进入开发阶段

## 0.1 登录登出，权限校验

模拟验证码发送。

登录校验采用jwt的方式，登出采用黑名单(user+token)。

导入openapi.json到apifox进行测试

注意设置前置后置（登录，token记录到变量，然后带上）

测试：需要mysql, redis
```
1、未登录状态测试查询商铺接口，返回401
2、测试发送验证码，返回验证码
3、测试手机号+验证码登录，成功
4、重新测试查询商铺接口，返回成功
5、登出成功
6、重新测试查询商铺接口或登出接口，返回401
```
