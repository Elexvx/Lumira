# Expert Drawer Single-Column QA

- Source visual truth: browser annotation Comment 1 on `https://bm.aiadc.org.cn/experts/management` supplied in the current task.
- Implementation screenshot: `C:/Users/Administrator/Documents/GitHub/Lumira/artifacts/qa-expert-form-single-column.png`
- Viewport: desktop, dark theme.
- State: create-expert drawer open with an empty form.

## Full-view comparison evidence

The annotated source and the browser-rendered local implementation were compared in the same task. The drawer width, dark theme, title, field order, form controls, footer, typography, and spacing tokens remain consistent with the existing management drawer. The requested structural change is isolated to the expert form grid: every visible field now occupies its own row.

## Focused region comparison evidence

The local browser screenshot shows `专家姓名`, `专家头衔`, `职务`, `所属机构`, `专业领域`, `联系电话`, and `手机号码` stacked vertically at the same left edge and full input width. The DOM snapshot continues with `邮箱`, `身份证号码`, `头像`, `专家简介`, `标签`, `状态`, `排序`, and `专家编码` in the same sequential order. No pair of visible fields shares a row.

## Findings

- No actionable P0, P1, or P2 visual differences remain for the annotated scope.
- The existing Ant Design control styles and drawer layout are preserved.
- The form grid now has one column at all supported viewport widths.
- Former full-span items resolve to the single grid column without changing field order.
- No custom imagery or new assets were introduced.

## Validation

- `corepack pnpm --dir lumira-ui run typecheck` passed.
- `corepack pnpm --dir lumira-ui run stylelint` passed.
- `git diff --check` passed.
- Browser DOM snapshot confirmed all expert fields render in the create drawer.
- Browser screenshot confirmed one field per row in the visible drawer region.

final result: passed
