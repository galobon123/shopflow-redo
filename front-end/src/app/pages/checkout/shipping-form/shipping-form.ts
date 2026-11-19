import { Component, inject } from '@angular/core';
import { ViewPanel } from "../../../directives/view-panel";
import { MatIcon } from "@angular/material/icon";
import { MatFormField } from "@angular/material/form-field";
import { MatInput } from "@angular/material/input";
import { MatError } from "@angular/material/form-field";
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-shipping-form',
  imports: [ViewPanel, MatIcon, MatFormField, MatInput, MatError, ReactiveFormsModule],
  templateUrl: './shipping-form.html',
  styleUrl: './shipping-form.css',
})
export class ShippingForm {
  private fb = inject(FormBuilder);

  form: FormGroup = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    address: ['', [Validators.required, Validators.maxLength(255)]],
    cityId: [null, [Validators.required]],
    zipCode: ['', [Validators.required, Validators.pattern(/^\d{1,4}$/)]],
  });
}
