��
��
^
AssignVariableOp
resource
value"dtype"
dtypetype"
validate_shapebool( �
�
BiasAdd

value"T	
bias"T
output"T""
Ttype:
2	"-
data_formatstringNHWC:
NHWCNCHW
8
Const
output"dtype"
valuetensor"
dtypetype
$
DisableCopyOnRead
resource�
.
Identity

input"T
output"T"	
Ttype
u
MatMul
a"T
b"T
product"T"
transpose_abool( "
transpose_bbool( "
Ttype:
2	
�
MergeV2Checkpoints
checkpoint_prefixes
destination_prefix"
delete_old_dirsbool("
allow_missing_filesbool( �

NoOp
M
Pack
values"T*N
output"T"
Nint(0"	
Ttype"
axisint 
C
Placeholder
output"dtype"
dtypetype"
shapeshape:
@
ReadVariableOp
resource
value"dtype"
dtypetype�
E
Relu
features"T
activations"T"
Ttype:
2	
o
	RestoreV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0�
l
SaveV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0�
?
Select
	condition

t"T
e"T
output"T"	
Ttype
H
ShardedFilename
basename	
shard

num_shards
filename
0
Sigmoid
x"T
y"T"
Ttype:

2
�
StatefulPartitionedCall
args2Tin
output2Tout"
Tin
list(type)("
Tout
list(type)("	
ffunc"
configstring "
config_protostring "
executor_typestring ��
@
StaticRegexFullMatch	
input

output
"
patternstring
L

StringJoin
inputs*N

output"

Nint("
	separatorstring 
�
VarHandleOp
resource"
	containerstring "
shared_namestring "
dtypetype"
shapeshape"#
allowed_deviceslist(string)
 �"serve*
2.13.0-rc12v2.13.0-rc0-26-g57633696be68�
n
output/biasVarHandleOp*
_output_shapes
: *
dtype0*
shape:*
shared_nameoutput/bias
g
output/bias/Read/ReadVariableOpReadVariableOpoutput/bias*
_output_shapes
:*
dtype0
v
output/kernelVarHandleOp*
_output_shapes
: *
dtype0*
shape
:*
shared_nameoutput/kernel
o
!output/kernel/Read/ReadVariableOpReadVariableOpoutput/kernel*
_output_shapes

:*
dtype0
h
fc1/biasVarHandleOp*
_output_shapes
: *
dtype0*
shape:*
shared_name
fc1/bias
a
fc1/bias/Read/ReadVariableOpReadVariableOpfc1/bias*
_output_shapes
:*
dtype0
p

fc1/kernelVarHandleOp*
_output_shapes
: *
dtype0*
shape
:b*
shared_name
fc1/kernel
i
fc1/kernel/Read/ReadVariableOpReadVariableOp
fc1/kernel*
_output_shapes

:b*
dtype0
n
serve_inputPlaceholder*'
_output_shapes
:���������b*
dtype0*
shape:���������b
�
StatefulPartitionedCallStatefulPartitionedCallserve_input
fc1/kernelfc1/biasoutput/kerneloutput/bias*
Tin	
2*
Tout
2*
_collective_manager_ids
 *'
_output_shapes
:���������*&
_read_only_resource_inputs
*-
config_proto

CPU

GPU 2J 8� *5
f0R.
,__inference_signature_wrapper___call___92838
x
serving_default_inputPlaceholder*'
_output_shapes
:���������b*
dtype0*
shape:���������b
�
StatefulPartitionedCall_1StatefulPartitionedCallserving_default_input
fc1/kernelfc1/biasoutput/kerneloutput/bias*
Tin	
2*
Tout
2*
_collective_manager_ids
 *'
_output_shapes
:���������*&
_read_only_resource_inputs
*-
config_proto

CPU

GPU 2J 8� *5
f0R.
,__inference_signature_wrapper___call___92851

NoOpNoOp
�
ConstConst"/device:CPU:0*
_output_shapes
: *
dtype0*�
value�B� B�
�
_endpoint_names
_endpoint_signatures
	variables
trainable_variables
non_trainable_variables
_all_variables
_misc_assets
	serve
	
signatures*
* 

	
serve* 
 
0
1
2
3*
 
0
1
2
3*
* 
 
0
1
2
3*
* 

trace_0* 
"
	serve
serving_default* 
* 
JD
VARIABLE_VALUE
fc1/kernel&variables/0/.ATTRIBUTES/VARIABLE_VALUE*
HB
VARIABLE_VALUEfc1/bias&variables/1/.ATTRIBUTES/VARIABLE_VALUE*
MG
VARIABLE_VALUEoutput/kernel&variables/2/.ATTRIBUTES/VARIABLE_VALUE*
KE
VARIABLE_VALUEoutput/bias&variables/3/.ATTRIBUTES/VARIABLE_VALUE*
* 
* 
* 
O
saver_filenamePlaceholder*
_output_shapes
: *
dtype0*
shape: 
�
StatefulPartitionedCall_2StatefulPartitionedCallsaver_filename
fc1/kernelfc1/biasoutput/kerneloutput/biasConst*
Tin

2*
Tout
2*
_collective_manager_ids
 *
_output_shapes
: * 
_read_only_resource_inputs
 *-
config_proto

CPU

GPU 2J 8� *'
f"R 
__inference__traced_save_92899
�
StatefulPartitionedCall_3StatefulPartitionedCallsaver_filename
fc1/kernelfc1/biasoutput/kerneloutput/bias*
Tin	
2*
Tout
2*
_collective_manager_ids
 *
_output_shapes
: * 
_read_only_resource_inputs
 *-
config_proto

CPU

GPU 2J 8� **
f%R#
!__inference__traced_restore_92920�n
�
�
,__inference_signature_wrapper___call___92838	
input
unknown:b
	unknown_0:
	unknown_1:
	unknown_2:
identity��StatefulPartitionedCall�
StatefulPartitionedCallStatefulPartitionedCallinputunknown	unknown_0	unknown_1	unknown_2*
Tin	
2*
Tout
2*
_collective_manager_ids
 *'
_output_shapes
:���������*&
_read_only_resource_inputs
*-
config_proto

CPU

GPU 2J 8� *#
fR
__inference___call___92824o
IdentityIdentity StatefulPartitionedCall:output:0^NoOp*
T0*'
_output_shapes
:���������<
NoOpNoOp^StatefulPartitionedCall*
_output_shapes
 "
identityIdentity:output:0*(
_construction_contextkEagerRuntime*.
_input_shapes
:���������b: : : : 22
StatefulPartitionedCallStatefulPartitionedCall:%!

_user_specified_name92834:%!

_user_specified_name92832:%!

_user_specified_name92830:%!

_user_specified_name92828:N J
'
_output_shapes
:���������b

_user_specified_nameinput
�,
�
__inference__traced_save_92899
file_prefix3
!read_disablecopyonread_fc1_kernel:b/
!read_1_disablecopyonread_fc1_bias:8
&read_2_disablecopyonread_output_kernel:2
$read_3_disablecopyonread_output_bias:
savev2_const

identity_9��MergeV2Checkpoints�Read/DisableCopyOnRead�Read/ReadVariableOp�Read_1/DisableCopyOnRead�Read_1/ReadVariableOp�Read_2/DisableCopyOnRead�Read_2/ReadVariableOp�Read_3/DisableCopyOnRead�Read_3/ReadVariableOpw
StaticRegexFullMatchStaticRegexFullMatchfile_prefix"/device:CPU:**
_output_shapes
: *
pattern
^s3://.*Z
ConstConst"/device:CPU:**
_output_shapes
: *
dtype0*
valueB B.parta
Const_1Const"/device:CPU:**
_output_shapes
: *
dtype0*
valueB B
_temp/part�
SelectSelectStaticRegexFullMatch:output:0Const:output:0Const_1:output:0"/device:CPU:**
T0*
_output_shapes
: f

StringJoin
StringJoinfile_prefixSelect:output:0"/device:CPU:**
N*
_output_shapes
: L

num_shardsConst*
_output_shapes
: *
dtype0*
value	B :f
ShardedFilename/shardConst"/device:CPU:0*
_output_shapes
: *
dtype0*
value	B : �
ShardedFilenameShardedFilenameStringJoin:output:0ShardedFilename/shard:output:0num_shards:output:0"/device:CPU:0*
_output_shapes
: s
Read/DisableCopyOnReadDisableCopyOnRead!read_disablecopyonread_fc1_kernel"/device:CPU:0*
_output_shapes
 �
Read/ReadVariableOpReadVariableOp!read_disablecopyonread_fc1_kernel^Read/DisableCopyOnRead"/device:CPU:0*
_output_shapes

:b*
dtype0i
IdentityIdentityRead/ReadVariableOp:value:0"/device:CPU:0*
T0*
_output_shapes

:ba

Identity_1IdentityIdentity:output:0"/device:CPU:0*
T0*
_output_shapes

:bu
Read_1/DisableCopyOnReadDisableCopyOnRead!read_1_disablecopyonread_fc1_bias"/device:CPU:0*
_output_shapes
 �
Read_1/ReadVariableOpReadVariableOp!read_1_disablecopyonread_fc1_bias^Read_1/DisableCopyOnRead"/device:CPU:0*
_output_shapes
:*
dtype0i

Identity_2IdentityRead_1/ReadVariableOp:value:0"/device:CPU:0*
T0*
_output_shapes
:_

Identity_3IdentityIdentity_2:output:0"/device:CPU:0*
T0*
_output_shapes
:z
Read_2/DisableCopyOnReadDisableCopyOnRead&read_2_disablecopyonread_output_kernel"/device:CPU:0*
_output_shapes
 �
Read_2/ReadVariableOpReadVariableOp&read_2_disablecopyonread_output_kernel^Read_2/DisableCopyOnRead"/device:CPU:0*
_output_shapes

:*
dtype0m

Identity_4IdentityRead_2/ReadVariableOp:value:0"/device:CPU:0*
T0*
_output_shapes

:c

Identity_5IdentityIdentity_4:output:0"/device:CPU:0*
T0*
_output_shapes

:x
Read_3/DisableCopyOnReadDisableCopyOnRead$read_3_disablecopyonread_output_bias"/device:CPU:0*
_output_shapes
 �
Read_3/ReadVariableOpReadVariableOp$read_3_disablecopyonread_output_bias^Read_3/DisableCopyOnRead"/device:CPU:0*
_output_shapes
:*
dtype0i

Identity_6IdentityRead_3/ReadVariableOp:value:0"/device:CPU:0*
T0*
_output_shapes
:_

Identity_7IdentityIdentity_6:output:0"/device:CPU:0*
T0*
_output_shapes
:�
SaveV2/tensor_namesConst"/device:CPU:0*
_output_shapes
:*
dtype0*�
value�B�B&variables/0/.ATTRIBUTES/VARIABLE_VALUEB&variables/1/.ATTRIBUTES/VARIABLE_VALUEB&variables/2/.ATTRIBUTES/VARIABLE_VALUEB&variables/3/.ATTRIBUTES/VARIABLE_VALUEB_CHECKPOINTABLE_OBJECT_GRAPHw
SaveV2/shape_and_slicesConst"/device:CPU:0*
_output_shapes
:*
dtype0*
valueBB B B B B �
SaveV2SaveV2ShardedFilename:filename:0SaveV2/tensor_names:output:0 SaveV2/shape_and_slices:output:0Identity_1:output:0Identity_3:output:0Identity_5:output:0Identity_7:output:0savev2_const"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 *
dtypes	
2�
&MergeV2Checkpoints/checkpoint_prefixesPackShardedFilename:filename:0^SaveV2"/device:CPU:0*
N*
T0*
_output_shapes
:�
MergeV2CheckpointsMergeV2Checkpoints/MergeV2Checkpoints/checkpoint_prefixes:output:0file_prefix"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 h

Identity_8Identityfile_prefix^MergeV2Checkpoints"/device:CPU:0*
T0*
_output_shapes
: S

Identity_9IdentityIdentity_8:output:0^NoOp*
T0*
_output_shapes
: �
NoOpNoOp^MergeV2Checkpoints^Read/DisableCopyOnRead^Read/ReadVariableOp^Read_1/DisableCopyOnRead^Read_1/ReadVariableOp^Read_2/DisableCopyOnRead^Read_2/ReadVariableOp^Read_3/DisableCopyOnRead^Read_3/ReadVariableOp*
_output_shapes
 "!

identity_9Identity_9:output:0*(
_construction_contextkEagerRuntime*
_input_shapes
: : : : : : 2(
MergeV2CheckpointsMergeV2Checkpoints20
Read/DisableCopyOnReadRead/DisableCopyOnRead2*
Read/ReadVariableOpRead/ReadVariableOp24
Read_1/DisableCopyOnReadRead_1/DisableCopyOnRead2.
Read_1/ReadVariableOpRead_1/ReadVariableOp24
Read_2/DisableCopyOnReadRead_2/DisableCopyOnRead2.
Read_2/ReadVariableOpRead_2/ReadVariableOp24
Read_3/DisableCopyOnReadRead_3/DisableCopyOnRead2.
Read_3/ReadVariableOpRead_3/ReadVariableOp:=9

_output_shapes
: 

_user_specified_nameConst:+'
%
_user_specified_nameoutput/bias:-)
'
_user_specified_nameoutput/kernel:($
"
_user_specified_name
fc1/bias:*&
$
_user_specified_name
fc1/kernel:C ?

_output_shapes
: 
%
_user_specified_namefile_prefix
�
�
,__inference_signature_wrapper___call___92851	
input
unknown:b
	unknown_0:
	unknown_1:
	unknown_2:
identity��StatefulPartitionedCall�
StatefulPartitionedCallStatefulPartitionedCallinputunknown	unknown_0	unknown_1	unknown_2*
Tin	
2*
Tout
2*
_collective_manager_ids
 *'
_output_shapes
:���������*&
_read_only_resource_inputs
*-
config_proto

CPU

GPU 2J 8� *#
fR
__inference___call___92824o
IdentityIdentity StatefulPartitionedCall:output:0^NoOp*
T0*'
_output_shapes
:���������<
NoOpNoOp^StatefulPartitionedCall*
_output_shapes
 "
identityIdentity:output:0*(
_construction_contextkEagerRuntime*.
_input_shapes
:���������b: : : : 22
StatefulPartitionedCallStatefulPartitionedCall:%!

_user_specified_name92847:%!

_user_specified_name92845:%!

_user_specified_name92843:%!

_user_specified_name92841:N J
'
_output_shapes
:���������b

_user_specified_nameinput
�
�
__inference___call___92824	
input;
)my_mlp_fc1_matmul_readvariableop_resource:b8
*my_mlp_fc1_biasadd_readvariableop_resource:>
,my_mlp_output_matmul_readvariableop_resource:;
-my_mlp_output_biasadd_readvariableop_resource:
identity��!my_mlp/fc1/BiasAdd/ReadVariableOp� my_mlp/fc1/MatMul/ReadVariableOp�$my_mlp/output/BiasAdd/ReadVariableOp�#my_mlp/output/MatMul/ReadVariableOp\
my_mlp/dropout/IdentityIdentityinput*
T0*'
_output_shapes
:���������b�
 my_mlp/fc1/MatMul/ReadVariableOpReadVariableOp)my_mlp_fc1_matmul_readvariableop_resource*
_output_shapes

:b*
dtype0�
my_mlp/fc1/MatMulMatMul my_mlp/dropout/Identity:output:0(my_mlp/fc1/MatMul/ReadVariableOp:value:0*
T0*'
_output_shapes
:����������
!my_mlp/fc1/BiasAdd/ReadVariableOpReadVariableOp*my_mlp_fc1_biasadd_readvariableop_resource*
_output_shapes
:*
dtype0�
my_mlp/fc1/BiasAddBiasAddmy_mlp/fc1/MatMul:product:0)my_mlp/fc1/BiasAdd/ReadVariableOp:value:0*
T0*'
_output_shapes
:���������f
my_mlp/fc1/ReluRelumy_mlp/fc1/BiasAdd:output:0*
T0*'
_output_shapes
:���������v
my_mlp/dropout_1/IdentityIdentitymy_mlp/fc1/Relu:activations:0*
T0*'
_output_shapes
:����������
#my_mlp/output/MatMul/ReadVariableOpReadVariableOp,my_mlp_output_matmul_readvariableop_resource*
_output_shapes

:*
dtype0�
my_mlp/output/MatMulMatMul"my_mlp/dropout_1/Identity:output:0+my_mlp/output/MatMul/ReadVariableOp:value:0*
T0*'
_output_shapes
:����������
$my_mlp/output/BiasAdd/ReadVariableOpReadVariableOp-my_mlp_output_biasadd_readvariableop_resource*
_output_shapes
:*
dtype0�
my_mlp/output/BiasAddBiasAddmy_mlp/output/MatMul:product:0,my_mlp/output/BiasAdd/ReadVariableOp:value:0*
T0*'
_output_shapes
:���������r
my_mlp/output/SigmoidSigmoidmy_mlp/output/BiasAdd:output:0*
T0*'
_output_shapes
:���������h
IdentityIdentitymy_mlp/output/Sigmoid:y:0^NoOp*
T0*'
_output_shapes
:����������
NoOpNoOp"^my_mlp/fc1/BiasAdd/ReadVariableOp!^my_mlp/fc1/MatMul/ReadVariableOp%^my_mlp/output/BiasAdd/ReadVariableOp$^my_mlp/output/MatMul/ReadVariableOp*
_output_shapes
 "
identityIdentity:output:0*(
_construction_contextkEagerRuntime*.
_input_shapes
:���������b: : : : 2F
!my_mlp/fc1/BiasAdd/ReadVariableOp!my_mlp/fc1/BiasAdd/ReadVariableOp2D
 my_mlp/fc1/MatMul/ReadVariableOp my_mlp/fc1/MatMul/ReadVariableOp2L
$my_mlp/output/BiasAdd/ReadVariableOp$my_mlp/output/BiasAdd/ReadVariableOp2J
#my_mlp/output/MatMul/ReadVariableOp#my_mlp/output/MatMul/ReadVariableOp:($
"
_user_specified_name
resource:($
"
_user_specified_name
resource:($
"
_user_specified_name
resource:($
"
_user_specified_name
resource:N J
'
_output_shapes
:���������b

_user_specified_nameinput
�
�
!__inference__traced_restore_92920
file_prefix-
assignvariableop_fc1_kernel:b)
assignvariableop_1_fc1_bias:2
 assignvariableop_2_output_kernel:,
assignvariableop_3_output_bias:

identity_5��AssignVariableOp�AssignVariableOp_1�AssignVariableOp_2�AssignVariableOp_3�
RestoreV2/tensor_namesConst"/device:CPU:0*
_output_shapes
:*
dtype0*�
value�B�B&variables/0/.ATTRIBUTES/VARIABLE_VALUEB&variables/1/.ATTRIBUTES/VARIABLE_VALUEB&variables/2/.ATTRIBUTES/VARIABLE_VALUEB&variables/3/.ATTRIBUTES/VARIABLE_VALUEB_CHECKPOINTABLE_OBJECT_GRAPHz
RestoreV2/shape_and_slicesConst"/device:CPU:0*
_output_shapes
:*
dtype0*
valueBB B B B B �
	RestoreV2	RestoreV2file_prefixRestoreV2/tensor_names:output:0#RestoreV2/shape_and_slices:output:0"/device:CPU:0*(
_output_shapes
:::::*
dtypes	
2[
IdentityIdentityRestoreV2:tensors:0"/device:CPU:0*
T0*
_output_shapes
:�
AssignVariableOpAssignVariableOpassignvariableop_fc1_kernelIdentity:output:0"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 *
dtype0]

Identity_1IdentityRestoreV2:tensors:1"/device:CPU:0*
T0*
_output_shapes
:�
AssignVariableOp_1AssignVariableOpassignvariableop_1_fc1_biasIdentity_1:output:0"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 *
dtype0]

Identity_2IdentityRestoreV2:tensors:2"/device:CPU:0*
T0*
_output_shapes
:�
AssignVariableOp_2AssignVariableOp assignvariableop_2_output_kernelIdentity_2:output:0"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 *
dtype0]

Identity_3IdentityRestoreV2:tensors:3"/device:CPU:0*
T0*
_output_shapes
:�
AssignVariableOp_3AssignVariableOpassignvariableop_3_output_biasIdentity_3:output:0"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 *
dtype0Y
NoOpNoOp"/device:CPU:0*&
 _has_manual_control_dependencies(*
_output_shapes
 �

Identity_4Identityfile_prefix^AssignVariableOp^AssignVariableOp_1^AssignVariableOp_2^AssignVariableOp_3^NoOp"/device:CPU:0*
T0*
_output_shapes
: U

Identity_5IdentityIdentity_4:output:0^NoOp_1*
T0*
_output_shapes
: v
NoOp_1NoOp^AssignVariableOp^AssignVariableOp_1^AssignVariableOp_2^AssignVariableOp_3*
_output_shapes
 "!

identity_5Identity_5:output:0*(
_construction_contextkEagerRuntime*
_input_shapes

: : : : : 2(
AssignVariableOp_1AssignVariableOp_12(
AssignVariableOp_2AssignVariableOp_22(
AssignVariableOp_3AssignVariableOp_32$
AssignVariableOpAssignVariableOp:+'
%
_user_specified_nameoutput/bias:-)
'
_user_specified_nameoutput/kernel:($
"
_user_specified_name
fc1/bias:*&
$
_user_specified_name
fc1/kernel:C ?

_output_shapes
: 
%
_user_specified_namefile_prefix"�L
saver_filename:0StatefulPartitionedCall_2:0StatefulPartitionedCall_38"
saved_model_main_op

NoOp*>
__saved_model_init_op%#
__saved_model_init_op

NoOp*�
serve�
-
input$
serve_input:0���������b<
output_00
StatefulPartitionedCall:0���������tensorflow/serving/predict*�
serving_default�
7
input.
serving_default_input:0���������b>
output_02
StatefulPartitionedCall_1:0���������tensorflow/serving/predict:�
�
_endpoint_names
_endpoint_signatures
	variables
trainable_variables
non_trainable_variables
_all_variables
_misc_assets
	serve
	
signatures"
_generic_user_object
 "
trackable_list_wrapper
+
	
serve"
trackable_dict_wrapper
<
0
1
2
3"
trackable_list_wrapper
<
0
1
2
3"
trackable_list_wrapper
 "
trackable_list_wrapper
<
0
1
2
3"
trackable_list_wrapper
 "
trackable_list_wrapper
�
trace_02�
__inference___call___92824�
���
FullArgSpec
args�

jargs_0
varargs
 
varkw
 
defaults
 

kwonlyargs� 
kwonlydefaults
 
annotations� *$�!
�
input���������bztrace_0
7
	serve
serving_default"
signature_map
 "
trackable_list_wrapper
:b2
fc1/kernel
:2fc1/bias
:2output/kernel
:2output/bias
�B�
__inference___call___92824input"�
���
FullArgSpec
args�

jargs_0
varargs
 
varkw
 
defaults
 

kwonlyargs� 
kwonlydefaults
 
annotations� *$�!
�
input���������b
�B�
,__inference_signature_wrapper___call___92838input"�
���
FullArgSpec
args� 
varargs
 
varkwjkwargs
defaults
 

kwonlyargs� 
kwonlydefaults
 
annotations� *
 
�B�
,__inference_signature_wrapper___call___92851input"�
���
FullArgSpec
args� 
varargs
 
varkwjkwargs
defaults
 

kwonlyargs� 
kwonlydefaults
 
annotations� *
 w
__inference___call___92824Y.�+
$�!
�
input���������b
� "!�
unknown����������
,__inference_signature_wrapper___call___92838t7�4
� 
-�*
(
input�
input���������b"3�0
.
output_0"�
output_0����������
,__inference_signature_wrapper___call___92851t7�4
� 
-�*
(
input�
input���������b"3�0
.
output_0"�
output_0���������