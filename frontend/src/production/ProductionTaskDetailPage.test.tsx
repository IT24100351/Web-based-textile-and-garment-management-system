import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  getProductionTaskDetail,
  getProductionTaskMaterialAvailability,
  getProductionTaskMaterialOptions,
  getProductionTaskMaterialUsage,
  recordProductionTaskMaterialUsage,
  startProductionTask,
  updateProductionTaskStatus,
  updateProductionTaskDetails,
  updateProductionTaskMaterials,
  updateProductionQualityControl,
} from "../api/productionTasks";
import { ProductionTaskDetailPage } from "./ProductionTaskDetailPage";

vi.mock("../api/productionTasks", () => ({
  getProductionTaskDetail: vi.fn(),
  getProductionTaskMaterialAvailability: vi.fn(),
  getProductionTaskMaterialOptions: vi.fn(),
  getProductionTaskMaterialUsage: vi.fn(),
  recordProductionTaskMaterialUsage: vi.fn(),
  startProductionTask: vi.fn(),
  updateProductionTaskStatus: vi.fn(),
  updateProductionTaskDetails: vi.fn(),
  updateProductionTaskMaterials: vi.fn(),
  updateProductionQualityControl: vi.fn(),
  getProductionTaskApiError: (error: unknown, fallback: string) => {
    const value = error as { message?: string; fields?: Record<string, string> };
    return { message: value?.message ?? fallback, fields: value?.fields ?? {} };
  },
}));

const mockedGetDetail = vi.mocked(getProductionTaskDetail);
const mockedGetAvailability = vi.mocked(getProductionTaskMaterialAvailability);
const mockedGetMaterialOptions = vi.mocked(getProductionTaskMaterialOptions);
const mockedGetUsage = vi.mocked(getProductionTaskMaterialUsage);
const mockedRecordUsage = vi.mocked(recordProductionTaskMaterialUsage);
const mockedStartProduction = vi.mocked(startProductionTask);
const mockedUpdateStatus = vi.mocked(updateProductionTaskStatus);
const mockedUpdateDetails = vi.mocked(updateProductionTaskDetails);
const mockedUpdateMaterials = vi.mocked(updateProductionTaskMaterials);
const mockedUpdateQuality = vi.mocked(updateProductionQualityControl);

const detail = {
  task: {
    id: 8801,
    taskNumber: "PRD-ABCDEF12345678901234",
    orderId: 7501,
    status: "PENDING" as const,
    startedAt: null,
    completedAt: null,
    createdAt: "2026-08-23T14:00:00Z",
    updatedAt: "2026-08-23T14:00:00Z",
    qualityControlResult: "PENDING" as const,
    qualityCheckedByUserId: null,
    qualityCheckedAt: null,
  },
  order: {
    orderId: 7501,
    orderNumber: "ORD-PRODUCTION-READY",
    customerId: 7101,
    currentStatus: "CONFIRMED" as const,
    readyForProduction: true,
    items: [{
      orderItemId: 7601,
      productId: 7201,
      variantId: 7301,
      quantity: 3,
      selectedSize: "L",
      selectedColor: "Navy",
    }],
  },
  workDetails: null,
  materialRequirements: [],
};

const emptyAvailability = {
  taskId: 8801,
  taskNumber: "PRD-ABCDEF12345678901234",
  taskStatus: "PENDING" as const,
  hasMaterialRequirements: false,
  allMaterialsAvailable: false,
  canStart: false,
  materials: [],
};

const emptyUsage = {
  taskId: 8801,
  taskNumber: "PRD-ABCDEF12345678901234",
  taskStatus: "PENDING" as const,
  usageRecorded: false,
  materials: [],
};

const materialOptions = [
  {
    inventoryMaterialId: 7801,
    materialCode: "FAB-PROD-001",
    materialName: "Cotton Twill",
    materialType: "FABRIC" as const,
    unitOfMeasure: "metre",
    currentQuantity: "120.000",
    status: "ACTIVE" as const,
    stockState: "SUFFICIENT" as const,
  },
  {
    inventoryMaterialId: 7802,
    materialCode: "RAW-PROD-002",
    materialName: "Polyester Thread",
    materialType: "RAW_MATERIAL" as const,
    unitOfMeasure: "cone",
    currentQuantity: "5.000",
    status: "ACTIVE" as const,
    stockState: "SUFFICIENT" as const,
  },
];

function renderPage(path = "/production/tasks/8801") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/production/tasks/:taskId" element={<ProductionTaskDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProductionTaskDetailPage", () => {
  beforeEach(() => {
    mockedGetDetail.mockResolvedValue(detail);
    mockedGetAvailability.mockResolvedValue(emptyAvailability);
    mockedGetMaterialOptions.mockResolvedValue(materialOptions);
    mockedGetUsage.mockResolvedValue(emptyUsage);
    mockedUpdateMaterials.mockResolvedValue({
      message: "Production material requirements saved successfully.",
      task: {
        ...detail,
        materialRequirements: [{
          inventoryMaterialId: 7801,
          materialCode: "FAB-PROD-001",
          materialName: "Cotton Twill",
          materialType: "FABRIC",
          unitOfMeasure: "metre",
          requiredQuantity: "25.500",
          currentQuantity: "120.000",
          inventoryStatus: "ACTIVE",
          availabilityState: "AVAILABLE",
          createdAt: "2026-08-23T15:00:00Z",
          updatedAt: "2026-08-23T15:00:00Z",
        }],
      },
    });
    mockedUpdateDetails.mockResolvedValue({
      message: "Production task details saved successfully.",
      task: {
        ...detail,
        workDetails: {
          workDetails: "Cut and stitch the linked order items.",
          workAssignment: "Sewing Line A",
          workNotes: "Inspect collars before finishing.",
          createdAt: "2026-08-23T14:20:00Z",
          updatedAt: "2026-08-23T14:20:00Z",
        },
      },
    });
    mockedStartProduction.mockResolvedValue({
      message: "Production started successfully.",
      task: {
        ...detail,
        task: {
          ...detail.task,
          status: "IN_PROGRESS",
          startedAt: "2026-08-23T16:00:00Z",
          updatedAt: "2026-08-23T16:00:00Z",
        },
        order: {
          ...detail.order,
          currentStatus: "IN_PRODUCTION",
          readyForProduction: false,
        },
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows linked order details and persists work assignment details", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Production task details" })).toBeInTheDocument();
    expect(screen.getByText(/Order ORD-PRODUCTION-READY · Order ID #7501/)).toBeInTheDocument();
    expect(screen.getByText(/Product #7201 · Variant #7301 · L · Navy/)).toBeInTheDocument();
    expect(screen.getByText("Required quantity: 3")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Manufacturing work details"), "Cut and stitch the linked order items.");
    await user.type(screen.getByLabelText("Work assignment"), "Sewing Line A");
    await user.type(screen.getByLabelText(/Work notes/), "Inspect collars before finishing.");
    await user.click(screen.getByRole("button", { name: "Save production details" }));

    await waitFor(() => expect(mockedUpdateDetails).toHaveBeenCalledWith(8801, {
      workDetails: "Cut and stitch the linked order items.",
      workAssignment: "Sewing Line A",
      workNotes: "Inspect collars before finishing.",
    }));
    expect(await screen.findByText("Production task details saved successfully.")).toBeInTheDocument();
  });

  it("rejects missing required work details before calling the API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Production task details" });
    await user.click(screen.getByRole("button", { name: "Save production details" }));

    expect(screen.getByText("Enter the manufacturing work details for this task.")).toBeInTheDocument();
    expect(screen.getByText(/Enter a work assignment/)).toBeInTheDocument();
    expect(mockedUpdateDetails).not.toHaveBeenCalled();
  });

  it("loads existing saved details for editing", async () => {
    mockedGetDetail.mockResolvedValue({
      ...detail,
      workDetails: {
        workDetails: "Existing manufacturing instructions",
        workAssignment: "Finishing Team 2",
        workNotes: null,
        createdAt: "2026-08-23T14:20:00Z",
        updatedAt: "2026-08-23T14:30:00Z",
      },
    });
    renderPage();

    expect(await screen.findByDisplayValue("Existing manufacturing instructions")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Finishing Team 2")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Edit work details" })).toBeInTheDocument();
  });

  it("assigns Inventory material IDs with required quantities and shows saved requirements", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Production task details" });

    await user.selectOptions(screen.getByLabelText("Inventory material 1"), "7801");
    await user.type(screen.getByLabelText("Required quantity 1"), "25.500");
    await user.click(screen.getByRole("button", { name: "Save material requirements" }));

    await waitFor(() => expect(mockedUpdateMaterials).toHaveBeenCalledWith(8801, [{
      inventoryMaterialId: 7801,
      requiredQuantity: "25.500",
    }]));
    expect(await screen.findByText("Production material requirements saved successfully.")).toBeInTheDocument();
    const savedRequirements = screen.getByLabelText("Saved material requirements");
    expect(within(savedRequirements).getByText("FAB-PROD-001 · Cotton Twill")).toBeInTheDocument();
    expect(within(savedRequirements).getByText("Required: 25.500 metre")).toBeInTheDocument();
  });

  it("rejects duplicate materials and invalid quantities before calling the API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Production task details" });

    await user.selectOptions(screen.getByLabelText("Inventory material 1"), "7801");
    await user.type(screen.getByLabelText("Required quantity 1"), "0");
    await user.click(screen.getByRole("button", { name: "Add material" }));
    await user.selectOptions(screen.getByLabelText("Inventory material 2"), "7801");
    await user.type(screen.getByLabelText("Required quantity 2"), "5");
    await user.click(screen.getByRole("button", { name: "Save material requirements" }));

    expect(screen.getByText("Enter a positive quantity with up to 3 decimal places.")).toBeInTheDocument();
    expect(screen.getByText("This inventory material is already assigned to the production task.")).toBeInTheDocument();
    expect(mockedUpdateMaterials).not.toHaveBeenCalled();
  });

  it("starts production only when the Inventory-backed readiness report is sufficient", async () => {
    const user = userEvent.setup();
    const sufficient = {
      ...emptyAvailability,
      hasMaterialRequirements: true,
      allMaterialsAvailable: true,
      canStart: true,
      materials: [{
        inventoryMaterialId: 7802,
        materialCode: "RAW-PROD-002",
        materialName: "Polyester Thread",
        materialType: "RAW_MATERIAL" as const,
        unitOfMeasure: "cone",
        requiredQuantity: "5.000",
        currentQuantity: "5.000",
        inventoryStatus: "ACTIVE" as const,
        availabilityState: "AVAILABLE" as const,
        createdAt: "2026-08-23T15:00:00Z",
        updatedAt: "2026-08-23T15:00:00Z",
      }],
    };
    mockedGetAvailability.mockResolvedValueOnce(sufficient).mockResolvedValue({
      ...sufficient,
      taskStatus: "IN_PROGRESS",
      canStart: false,
    });

    renderPage();
    expect(await screen.findByText("All required material quantities are currently available.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Start production" }));

    await waitFor(() => expect(mockedStartProduction).toHaveBeenCalledWith(8801));
    expect(await screen.findByText("Production started successfully.")).toBeInTheDocument();
    expect(screen.getByText(/Production status:/)).toHaveTextContent("IN PROGRESS");
  });

  it("records approved material usage once and shows the Inventory deduction history", async () => {
    const user = userEvent.setup();
    const inProgressDetail = {
      ...detail,
      task: {
        ...detail.task,
        status: "IN_PROGRESS" as const,
        startedAt: "2026-08-23T16:00:00Z",
      },
      materialRequirements: [{
        inventoryMaterialId: 7801,
        materialCode: "FAB-PROD-001",
        materialName: "Cotton Twill",
        materialType: "FABRIC" as const,
        unitOfMeasure: "metre",
        requiredQuantity: "20.000",
        currentQuantity: "120.000",
        inventoryStatus: "ACTIVE" as const,
        availabilityState: "AVAILABLE" as const,
        createdAt: "2026-08-23T15:00:00Z",
        updatedAt: "2026-08-23T15:00:00Z",
      }],
    };
    const recordedUsage = {
      ...emptyUsage,
      taskStatus: "IN_PROGRESS" as const,
      usageRecorded: true,
      materials: [{
        id: 9901,
        inventoryMaterialId: 7801,
        materialCode: "FAB-PROD-001",
        materialName: "Cotton Twill",
        unitOfMeasure: "metre",
        quantityUsed: "20.000",
        remainingQuantity: "100.000",
        recordedByUserId: 7999,
        recordedAt: "2026-08-23T17:00:00Z",
      }],
    };
    mockedGetDetail.mockResolvedValue(inProgressDetail);
    mockedGetAvailability.mockResolvedValue({
      ...emptyAvailability,
      taskStatus: "IN_PROGRESS",
      hasMaterialRequirements: true,
      allMaterialsAvailable: true,
      canStart: false,
      materials: inProgressDetail.materialRequirements,
    });
    mockedRecordUsage.mockResolvedValue({
      message: "Production material usage recorded successfully.",
      usage: recordedUsage,
    });

    renderPage();
    expect(await screen.findByRole("button", { name: "Record material usage" })).toBeInTheDocument();
    expect(screen.getByText("Material requirements are locked because production has already started.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Record material usage" }));

    await waitFor(() => expect(mockedRecordUsage).toHaveBeenCalledWith(8801));
    expect(await screen.findByText("Production material usage recorded successfully.")).toBeInTheDocument();
    expect(screen.getByText("Used: 20.000 metre · Inventory remaining: 100.000 metre")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Record material usage" })).not.toBeInTheDocument();
  });

  it("records a simple quality-control result after material usage", async () => {
    const user = userEvent.setup();
    const inProgressDetail = {
      ...detail,
      task: { ...detail.task, status: "IN_PROGRESS" as const, startedAt: "2026-08-23T16:00:00Z" },
      materialRequirements: [{
        inventoryMaterialId: 7801,
        materialCode: "FAB-PROD-001",
        materialName: "Cotton Twill",
        materialType: "FABRIC" as const,
        unitOfMeasure: "metre",
        requiredQuantity: "20.000",
        currentQuantity: "100.000",
        inventoryStatus: "ACTIVE" as const,
        availabilityState: "AVAILABLE" as const,
        createdAt: "2026-08-23T15:00:00Z",
        updatedAt: "2026-08-23T15:00:00Z",
      }],
    };
    mockedGetDetail.mockResolvedValue(inProgressDetail);
    mockedGetAvailability.mockResolvedValue({
      ...emptyAvailability,
      taskStatus: "IN_PROGRESS",
      hasMaterialRequirements: true,
      allMaterialsAvailable: true,
      canStart: false,
      materials: inProgressDetail.materialRequirements,
    });
    mockedGetUsage.mockResolvedValue({
      ...emptyUsage,
      taskStatus: "IN_PROGRESS",
      usageRecorded: true,
      materials: [],
    });
    mockedUpdateQuality.mockResolvedValue({
      message: "Quality-control result recorded successfully.",
      task: {
        ...inProgressDetail,
        task: {
          ...inProgressDetail.task,
          qualityControlResult: "PASSED",
          qualityCheckedByUserId: 7999,
          qualityCheckedAt: "2026-08-23T17:30:00Z",
        },
      },
    });

    renderPage();
    expect(await screen.findByRole("heading", { name: "Quality control" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Mark QC passed" }));
    await waitFor(() => expect(mockedUpdateQuality).toHaveBeenCalledWith(8801, "PASSED"));
    expect(await screen.findByText("Quality-control result recorded successfully.")).toBeInTheDocument();
  });

  it("completes in-progress production only after material usage and reflects Order progress", async () => {
    const user = userEvent.setup();
    const inProgressDetail = {
      ...detail,
      task: {
        ...detail.task,
        status: "IN_PROGRESS" as const,
        startedAt: "2026-08-23T16:00:00Z",
        qualityControlResult: "PASSED" as const,
        qualityCheckedByUserId: 7999,
        qualityCheckedAt: "2026-08-23T17:30:00Z",
      },
      order: {
        ...detail.order,
        currentStatus: "IN_PRODUCTION" as const,
        readyForProduction: false,
      },
      materialRequirements: [{
        inventoryMaterialId: 7801,
        materialCode: "FAB-PROD-001",
        materialName: "Cotton Twill",
        materialType: "FABRIC" as const,
        unitOfMeasure: "metre",
        requiredQuantity: "20.000",
        currentQuantity: "100.000",
        inventoryStatus: "ACTIVE" as const,
        availabilityState: "AVAILABLE" as const,
        createdAt: "2026-08-23T15:00:00Z",
        updatedAt: "2026-08-23T15:00:00Z",
      }],
    };
    mockedGetDetail.mockResolvedValue(inProgressDetail);
    mockedGetAvailability.mockResolvedValue({
      ...emptyAvailability,
      taskStatus: "IN_PROGRESS",
      hasMaterialRequirements: true,
      allMaterialsAvailable: true,
      canStart: false,
      materials: inProgressDetail.materialRequirements,
    });
    mockedGetUsage.mockResolvedValue({
      taskId: 8801,
      taskNumber: detail.task.taskNumber,
      taskStatus: "IN_PROGRESS",
      usageRecorded: true,
      materials: [{
        id: 9901,
        inventoryMaterialId: 7801,
        materialCode: "FAB-PROD-001",
        materialName: "Cotton Twill",
        unitOfMeasure: "metre",
        quantityUsed: "20.000",
        remainingQuantity: "100.000",
        recordedByUserId: 7999,
        recordedAt: "2026-08-23T17:00:00Z",
      }],
    });
    mockedUpdateStatus.mockResolvedValue({
      message: "Production status updated successfully.",
      task: {
        ...inProgressDetail,
        task: {
          ...inProgressDetail.task,
          status: "COMPLETED",
          completedAt: "2026-08-23T18:00:00Z",
          updatedAt: "2026-08-23T18:00:00Z",
        },
        order: {
          ...inProgressDetail.order,
          currentStatus: "READY_FOR_DELIVERY",
        },
      },
    });

    renderPage();
    expect(await screen.findByText(/Order progress:/)).toHaveTextContent("IN PRODUCTION");
    expect(screen.getByRole("button", { name: "Mark production completed" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Mark production completed" }));

    await waitFor(() => expect(mockedUpdateStatus).toHaveBeenCalledWith(8801, "COMPLETED"));
    expect(await screen.findByText("Production status updated successfully.")).toBeInTheDocument();
    expect(screen.getByText(/Production status:/)).toHaveTextContent("COMPLETED");
    expect(screen.getByText(/Order progress:/)).toHaveTextContent("READY FOR DELIVERY");
  });

  it("shows exact shortage details and disables start when required stock is insufficient", async () => {
    mockedGetAvailability.mockResolvedValue({
      ...emptyAvailability,
      hasMaterialRequirements: true,
      allMaterialsAvailable: false,
      canStart: false,
      materials: [{
        inventoryMaterialId: 7802,
        materialCode: "RAW-PROD-002",
        materialName: "Polyester Thread",
        materialType: "RAW_MATERIAL" as const,
        unitOfMeasure: "cone",
        requiredQuantity: "5.001",
        currentQuantity: "5.000",
        inventoryStatus: "ACTIVE" as const,
        availabilityState: "INSUFFICIENT_STOCK" as const,
        createdAt: "2026-08-23T15:00:00Z",
        updatedAt: "2026-08-23T15:00:00Z",
      }],
    });

    renderPage();
    expect(await screen.findByText("Production cannot start until these shortages are resolved:")).toBeInTheDocument();
    expect(screen.getByText("Required: 5.001 cone · Available now: 5.000 cone")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start production" })).toBeDisabled();
    expect(mockedStartProduction).not.toHaveBeenCalled();
  });

});
