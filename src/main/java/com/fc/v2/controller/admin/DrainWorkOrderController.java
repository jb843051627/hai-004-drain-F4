package com.fc.v2.controller.admin;

import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TDrainWorkOrder;
import com.fc.v2.service.ITDrainWorkOrderService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 排涝抢险作业单 Controller（state-machine 形状：五档流转入口）
 *
 * 只暴露开单/推进/撤回/台账倒盘，不提供自由编辑入口——
 * 办结销号后在任何接口上都改不动、抹不掉，确有错漏只能另开补单。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "排涝抢险作业单")
@Controller
@RequestMapping("/drainWorkOrder")
public class DrainWorkOrderController extends BaseController {

    private final String prefix = "admin/drainWorkOrder";

    @Autowired
    private ITDrainWorkOrderService drainWorkOrderService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("drainWorkOrder:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "排涝抢险作业单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("drainWorkOrder:list")
    @ResponseBody
    public ResultTable list(TDrainWorkOrder record) {
        QueryWrapper<TDrainWorkOrder> queryWrapper = new QueryWrapper<TDrainWorkOrder>();
        if (record.getBizNo() != null && !record.getBizNo().trim().isEmpty()) {
            queryWrapper.like("biz_no", record.getBizNo().trim());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().trim().isEmpty()) {
            queryWrapper.eq("site_no", record.getSiteNo().trim());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        queryWrapper.orderByDesc("create_time");
        startPage();
        com.github.pagehelper.PageInfo<TDrainWorkOrder> page =
                new com.github.pagehelper.PageInfo<TDrainWorkOrder>(drainWorkOrderService.selectTDrainWorkOrderList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "排涝抢险作业单详情", action = "detail")
    @ApiOperation(value = "单据详情", notes = "单据详情")
    @GetMapping("/detail")
    @RequiresPermissions("drainWorkOrder:list")
    @ResponseBody
    public AjaxResult detail(@RequestParam Long id) {
        TDrainWorkOrder order = drainWorkOrderService.selectTDrainWorkOrderById(id);
        if (order == null) {
            return AjaxResult.error("作业单不存在");
        }
        return AjaxResult.successData(200, order);
    }

    @Log(title = "排涝抢险作业单留痕", action = "trail")
    @ApiOperation(value = "逐档流水倒盘", notes = "谁把单子挪进哪一档、什么时刻挪的，倒序返回，作废旧笔也翻得到")
    @GetMapping("/trail")
    @RequiresPermissions("drainWorkOrder:list")
    @ResponseBody
    public AjaxResult trail(@RequestParam Long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("order", drainWorkOrderService.selectTDrainWorkOrderById(id));
        data.put("trails", drainWorkOrderService.trail(id));
        return AjaxResult.successData(200, data);
    }

    @Log(title = "排涝抢险作业单开单", action = "open")
    @ApiOperation(value = "接单开单", notes = "落接单档(0)并把经办人写进单里；该站压着未销单不许重复开")
    @PostMapping("/open")
    @RequiresPermissions("drainWorkOrder:open")
    @ResponseBody
    public AjaxResult open(@RequestParam String siteNo, String opinion) {
        TDrainWorkOrder order = drainWorkOrderService.open(siteNo, ShiroUtils.getLoginName(), opinion);
        return AjaxResult.success("已接单开单，作业单号：" + order.getBizNo());
    }

    @Log(title = "排涝抢险作业单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "一档一档往前挪，经办人与时刻逐笔入流水；同一档不许挪第二回")
    @PostMapping("/advance")
    @RequiresPermissions("drainWorkOrder:advance")
    @ResponseBody
    public AjaxResult advance(@RequestParam Long id, String opinion) {
        drainWorkOrderService.advance(id, ShiroUtils.getLoginName(), opinion);
        return AjaxResult.success("已推进一档");
    }

    @Log(title = "排涝抢险作业单撤回", action = "rollback")
    @ApiOperation(value = "撤回前一档重办", notes = "退回档旧记一律作废，不与重办新记并排挂在同一张单上")
    @PostMapping("/rollback")
    @RequiresPermissions("drainWorkOrder:rollback")
    @ResponseBody
    public AjaxResult rollback(@RequestParam Long id, String opinion) {
        drainWorkOrderService.rollback(id, ShiroUtils.getLoginName(), opinion);
        return AjaxResult.success("已撤回前一档重办，该档旧记作废");
    }
}
