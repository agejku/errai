/*
 * Copyright 2012 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.errai.ui.rebind;

import com.google.common.base.Strings;
import com.google.gwt.dom.client.Element;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.builder.impl.ObjectBuilder;
import org.jboss.errai.codegen.exception.GenerationException;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.ioc.client.api.CodeDecorator;
import org.jboss.errai.ioc.rebind.ioc.extension.IOCDecoratorExtension;
import org.jboss.errai.ioc.rebind.ioc.injector.api.InjectableInstance;
import org.jboss.errai.ui.shared.ElementWrapperWidget;
import org.jboss.errai.ui.shared.Template;
import org.jboss.errai.ui.shared.api.annotations.Bound;
import org.jboss.errai.ui.shared.api.annotations.DataField;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Store all injected {@link DataField} {@link Statement} instances into the
 * aggregate {@link Map} for this composite {@link Template}.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * @author Christian Sadilek <csadilek@redhat.com>
 */
@CodeDecorator
public class DataFieldCodeDecorator extends IOCDecoratorExtension<DataField> {

  public DataFieldCodeDecorator(Class<DataField> decoratesWith) {
    super(decoratesWith);
  }

  @Override
  public List<? extends Statement> generateDecorator(InjectableInstance<DataField> ctx) {
    ctx.ensureMemberExposed();
    Statement instance = ctx.getValueStatement();
    String name = getTemplateDataFieldName(ctx.getAnnotation(), ctx.getMemberName());
    if (ctx.getEnclosingType().isAssignableTo(Element.class)) {
      if (ctx.isAnnotationPresent(Inject.class)) {
        throw new GenerationException("@DataField [" + name + "] in class ["
                + ctx.getEnclosingType().getFullyQualifiedName() + "] is of type ["
                + ctx.getElementTypeOrMethodReturnType().getFullyQualifiedName()
                + "] which does not support @Inject; this instance must be created manually.");
      }
      instance = ObjectBuilder.newInstanceOf(ElementWrapperWidget.class).withParameters(instance);
    }
    saveDataField(ctx, ctx.getEnclosingType(), name, ctx.getMemberName(), ctx.getAnnotation(Bound.class), instance);

    return new ArrayList<Statement>();

  }

  private void saveDataField(InjectableInstance<DataField> ctx, MetaClass type, String name, String fieldName,
      Bound bound, Statement instance) {
    dataFieldMap(ctx, ctx.getEnclosingType()).put(name, instance);
    dataFieldTypeMap(ctx, ctx.getEnclosingType()).put(name, type);
    
    if (bound != null) {
      dataFieldBoundMap(ctx, ctx.getEnclosingType()).put(fieldName, new BoundDataField(bound, instance, name));
    }
  }

  private String getTemplateDataFieldName(DataField annotation, String deflt) {
    String value = Strings.nullToEmpty(annotation.value()).trim();
    return value.isEmpty() ? deflt : value;
  }

  /**
   * Get the map of {@link DataField} names and {@link Statement} instances.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Statement> dataFieldMap(InjectableInstance<?> ctx, MetaClass templateType) {
    String dataFieldMapName = dataFieldMapName(templateType);

    Map<String, Statement> dataFields = (Map<String, Statement>) ctx.getInjectionContext().getAttribute(
            dataFieldMapName);
    if (dataFields == null) {
      dataFields = new LinkedHashMap<String, Statement>();
      ctx.getInjectionContext().setAttribute(dataFieldMapName, dataFields);
    }

    return dataFields;
  }

  /**
   * Get the map of {@link DataField} names and {@link Bound} instances.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, BoundDataField> dataFieldBoundMap(InjectableInstance<?> ctx, MetaClass templateType) {
    String mapName = dataFieldBoundMapName(templateType);

    Map<String, BoundDataField> bindings = (Map<String, BoundDataField>) 
      ctx.getInjectionContext().getAttribute(mapName);
    
    if (bindings == null) {
      bindings = new LinkedHashMap<String, BoundDataField>();
      ctx.getInjectionContext().setAttribute(mapName, bindings);
    }

    return bindings;
  }
  
  /**
   * Get the map of {@link DataField} names and {@link MetaClass} types.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, MetaClass> dataFieldTypeMap(InjectableInstance<?> ctx, MetaClass templateType) {
    String dataFieldTypeMapName = dataFieldTypeMapName(templateType);

    Map<String, MetaClass> dataFieldTypes = (Map<String, MetaClass>) ctx.getInjectionContext().getAttribute(
            dataFieldTypeMapName);
    if (dataFieldTypes == null) {
      dataFieldTypes = new LinkedHashMap<String, MetaClass>();
      ctx.getInjectionContext().setAttribute(dataFieldTypeMapName, dataFieldTypes);
    }

    return dataFieldTypes;
  }

  /**
   * Get the aggregate map of {@link DataField} names and {@link Statement}
   * instances for the given {@link MetaClass} type and all ancestors returned
   * by {@link MetaClass#getSuperClass()}.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Statement> aggregateDataFieldMap(InjectableInstance<?> ctx, MetaClass componentType) {

    Map<String, Statement> result = new LinkedHashMap<String, Statement>();

    if (componentType.getSuperClass() != null) {
      result.putAll(aggregateDataFieldMap(ctx, componentType.getSuperClass()));
    }

    Map<String, Statement> dataFields = (Map<String, Statement>) ctx.getInjectionContext().getAttribute(
            dataFieldMapName(componentType));
    if (dataFields != null) {
      result.putAll(dataFields);
    }

    return result;
  }
  
  /**
   * Get the aggregate map of {@link DataField} names to {@link Bound} instances.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, BoundDataField> aggregateDataFieldBoundMap(InjectableInstance<?> ctx, 
      MetaClass componentType) {

    Map<String, BoundDataField> result = new LinkedHashMap<String, BoundDataField>();

    if (componentType.getSuperClass() != null) {
      result.putAll(aggregateDataFieldBoundMap(ctx, componentType.getSuperClass()));
    }

    Map<String, BoundDataField> dataFields = (Map<String, BoundDataField>) 
      ctx.getInjectionContext().getAttribute(dataFieldBoundMapName(componentType));
    if (dataFields != null) {
      result.putAll(dataFields);
    }

    return result;
  }

  /**
   * Get the aggregate map of {@link DataField} names and {@link MetaClass}
   * types for the given {@link MetaClass} component type and all ancestors
   * returned by {@link MetaClass#getSuperClass()}.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, MetaClass> aggregateDataFieldTypeMap(InjectableInstance<?> ctx, MetaClass componentType) {

    Map<String, MetaClass> result = new LinkedHashMap<String, MetaClass>();

    if (componentType.getSuperClass() != null) {
      result.putAll(aggregateDataFieldTypeMap(ctx, componentType.getSuperClass()));
    }

    Map<String, MetaClass> dataFields = (Map<String, MetaClass>) ctx.getInjectionContext().getAttribute(
            dataFieldTypeMapName(componentType));
    
    if (dataFields != null) {
      result.putAll(dataFields);
    }

    return result;
  }

  /**
   * Using the given composite {@link Template} type, return the name of the map
   * of {@link DataField} names and variable {@link Statement} instances.
   */
  private static final String dataFieldMapName(MetaClass composite) {
    return DataFieldCodeDecorator.class.getName() + "_DATA_FIELD_MAP_" + composite.getName();
  }
  
  /**
   * Using the given composite {@link Template} type, return the name of the map
   * of {@link DataField} names and {@link Bound} instances.
   */
  private static final String dataFieldBoundMapName(MetaClass composite) {
    return DataFieldCodeDecorator.class.getName() + "_DATA_FIELD_BOUND_MAP_" + composite.getName();
  }

  /**
   * Using the given composite {@link Template} type, return the name of the map
   * of {@link DataField} names and variable {@link MetaClass} types.
   */
  private static final String dataFieldTypeMapName(MetaClass composite) {
    return DataFieldCodeDecorator.class.getName() + "_DATA_FIELD_TYPE_MAP_" + composite.getName();
  }
}